package graphsc.app

import graphsc._


object Reformat {

  trait MyType {
    def unify(other: MyType): MyType = (this, other) match {
      case (EmptyType(), t) => t
      case (t, EmptyType()) => t
      case (a, b) if a == b => a
      case (SumType(s1), SumType(s2)) => SumType(s1 | s2)
      case (FunType(a1, b1), FunType(a2, b2)) => FunType(a1 unify a2, b1 unify b2)
      case (t1:MutType, t2) =>
        assert(!t2.containsStrictly(t1))
        if(t2.contains(t1))
          t2
        else
          t1.ref match {
            case None =>
              t1.ref = Some(t2)
              t1
            case Some(t) =>
              t1.ref = Some(t unify t2)
              t1
          }
      case (t1, t2:MutType) => t2 unify t1
      case _ => AnyType()
    }
    
    def containsStrictly(t: MyType): Boolean = this match {
      case FunType(t1, t2) => t1.contains(t) || t2.contains(t)
      case t1:MutType => t1.ref.map(_.containsStrictly(t)).getOrElse(false)
      case _ => false
    }
    
    def contains(t: MyType): Boolean = this match {
      case _ if this == t => true
      case FunType(t1, t2) => t1.contains(t) || t2.contains(t)
      case t1:MutType => t1.ref.map(_.contains(t)).getOrElse(false)
      case _ => false
    }
    
    def deref: MyType = this match {
      case t:MutType => t.ref.map(_.deref).getOrElse(t)
      case FunType(f, t) => FunType(f.deref, t.deref)
      case _ => this
    }
    
    def |(other: MyType): MyType = (this, other) match {
      case (EmptyType(), t) => t
      case (t, EmptyType()) => t
      case (a, b) if a == b => a
      case (SumType(s1), SumType(s2)) => SumType(s1 | s2)
      case (FunType(a1, b1), FunType(a2, b2)) => FunType(a1 & a2, b1 | b2)
      case (t1:MutType, t2) =>
        t1.ref match {
          case None => t2
          case Some(t) => t | t2
        }
      case (t1, t2:MutType) => t2 | t1
      case _ => AnyType()
    }
    
    def &(other: MyType): MyType = (this, other) match {
      case (AnyType(), t) => t
      case (t, AnyType()) => t
      case (a, b) if a == b => a
      case (SumType(s1), SumType(s2)) => SumType(s1 & s2)
      case (FunType(a1, b1), FunType(a2, b2)) => FunType(a1 | a2, b1 & b2)
      case (t1:MutType, t2) =>
        t1.ref match {
          case None => t2
          case Some(t) => t & t2
        }
      case (t1, t2:MutType) => t2 & t1
      case _ => EmptyType()
    }
  }
  
  case class SumType(cs: Set[String]) extends MyType
  case class FunType(from: MyType, to: MyType) extends MyType
  case class EmptyType() extends MyType
  case class AnyType() extends MyType
  
  class MutType(var ref: Option[MyType] = None) extends MyType {
    override def toString = "(" + ref.map(_.toString).getOrElse(super.toString) + ")"
  }
  
  def mergeSets[T](sets: List[Set[T]]): List[Set[T]] = sets match {
    case Nil => Nil
    case (h::t) =>
      val tail = mergeSets(t)
      var cur = h
      var newtail: List[Set[T]] = Nil
      for(s <- tail) {
        if((cur & s).nonEmpty)
          cur = cur | s
        else
          newtail = s :: newtail
      }
      cur :: newtail
  }
  
  // This function is very slow, may hang or even give incorrect result if it cannot infer types,
  // so don't use it unless you know what you are doing
  def guessTypes(prog: Program): 
        (Map[String, Set[String]], Map[String, List[MyType]], Map[ExprCaseOf, MyType]) = {
    val types1 =
      mergeSets(prog.allSubExprs.collect{
        case ExprCaseOf(_, cs) => cs.map(_._1).toSet
      }.toList)
      
    val cons2type1 =
      types1.map(s => s.toList.map((_, s))).flatten.toMap
    
    val othertype =
      prog.allSubExprs.collect{
        case ExprConstr(c) if !cons2type1.contains(c) => c
      }.toSet
    
    val cons2type = 
      if(othertype.isEmpty) cons2type1 
      else cons2type1 ++ othertype.map((_, othertype))
      
    val argtypes = collection.mutable.Map[(String, Int), List[MyType]]()
    
    for((s,bs) <- prog.defs)
      go(ExprFun(s))
      
    for(p <- prog.prove; e <- p.allExprs)
      go(e)
      
    def go(e: Expr, hist: Map[String, MyType] = Map(), 
                    varts: Map[String, MyType] = Map()): MyType =  e match {
      case ExprFun(n) if hist.contains(n) => hist(n)
      case ExprFun(n) =>
        var t: MyType = new MutType()
        for(b <- prog.defs(n)) {
          t = t unify go(b, hist + (n -> t), varts)
        }
        t
      case ExprVar(v) => varts.getOrElse(v, new MutType())
      case ExprUnused() => new MutType()
      case ExprLambda(vs, body) =>
        val lst = vs.map(v => (v, new MutType()))
        val bt = go(body, hist, varts ++ lst)
        (lst.map(_._2) :\ bt)(FunType(_, _))
      case ExprLet(e, bs) =>
        go(e, hist, varts ++ bs.map(p => (p._1, go(p._2, hist, varts))))
      case ExprConstr(c) => go(ExprCall(ExprConstr(c), Nil), hist, varts)
      case ExprCall(ExprConstr(c), as) =>
        for((a,i) <- as.zipWithIndex)
          argtypes.withDefaultValue(Nil)((c,i)) ::= go(a, hist, varts)
        SumType(cons2type(c))
      case ExprCall(f, Nil) => go(f, hist, varts)
      case ExprCall(f, List(e)) =>
        val res = new MutType()
        go(f, hist, varts) unify FunType(go(e, hist, varts), res)
        res
      case ExprCall(f, a :: as) => go(ExprCall(ExprCall(f, List(a)), as), hist, varts)
      case ExprCaseOf(e, cs) =>
        go(e, hist, varts) unify SumType(cons2type(cs(0)._1))
        val ts =
          for((c, vs, b) <- cs) yield {
            val lst = vs.map(v => (v, new MutType()))
            for(((_, t), i) <- lst.zipWithIndex)
              argtypes.withDefaultValue(Nil)((c,i)) ::= t
            go(b, hist, varts ++ lst)
          }
        ts.reduce(_ unify _)
    }
      
    val argtypesred = //argtypes.mapValues(_.map(_.deref).toSet) 
      //argtypes.map(p => (p._1, p._2.reduce(_ | _)))
      argtypes.mapValues(_.map(_.deref)).map(p => (p._1, p._2.reduce(_ unify _).deref))
    
    (cons2type, 
     argtypesred.groupBy(_._1._1).mapValues(x => x.toList.sortBy(_._1._2).map(_._2))
       .withDefaultValue(Nil), 
     null)
  }
  
  def guessTypevars(cons2type: Map[String, Set[String]], argtypes: Map[String, List[MyType]]):
        Map[Set[String], Set[String]] = {
    val types = cons2type.values.toSet
    val state = collection.mutable.Map[Set[String], Set[String]]().withDefaultValue(Set())
    
    def typeVars(t: MyType, pref: String): Set[String] = t match {
      case SumType(s) if types(s) => state(s)
      case FunType(f, t) => typeVars(f, pref + "f_") ++ typeVars(t, pref + "t_")
      case _ => Set(pref + "x")
    } 
    
    var changed = true
    while(changed) {
      changed = false
      for(t <- types) {
        val newvars = 
          (for(c <- t.toList; (at,j) <- argtypes(c).zipWithIndex) yield 
              typeVars(at, "a_" + typeName(t) + "_" + c + "_" + j + "_")
          ).flatten.toSet
        if(state(t) != newvars) {
          changed = true
          state(t) = newvars
        }
      }
    }
    state.toMap.withDefaultValue(Set())
  }
  
  def typeName(t: Set[String]): String = {
    if(t == Set("S", "Z")) "Nat"
    else if(t == Set("C", "N")) "Lst"
    else if(t == Set("T", "F")) "Boolean"
    else if(t == Set("Cons", "Nil")) "List"
    else "T_" + t.mkString("_")
  }
  
  def apply(prog: Program, fmt: String) {
    val (cons2type, argtypes, _) = guessTypes(prog)
    val types = cons2type.values.toSet
    
    if(fmt == "hipspec" || fmt == "hipspec-total") {
      println("{-# LANGUAGE DeriveDataTypeable #-}")
      println("module Test where\n")
      println("import qualified Prelude")
      println("import Prelude (Eq, Ord, Show, Int, ($), (==), (-), (/))")
      println("import Control.Applicative ((<$>), (<*>))")
      println("import HipSpec")
    }
    
    println()
    
    if(fmt == "hosc" || fmt == "hipspec-total") {
      val tvars = guessTypevars(cons2type, argtypes)
      
      def typeToStr(t: MyType, pref: String): String = t match {
        case SumType(s) if types(s) => 
          "(" + typeName(s) + tvars(s).mkString(" ", " ", "") + ")"
        case FunType(f, t) => 
          "(" + typeToStr(f, pref + "f_") + " -> " + typeToStr(f, pref + "t_") + ")"
        case _ => pref + "x"
      }
      
      for(t <- types) {
        val conslist =
          for(c <- t.toList) yield {
            val args =
              for((at,j) <- argtypes(c).zipWithIndex) yield 
                typeToStr(at, "a_" + typeName(t) + "_" + c + "_" + j + "_")
            c + args.mkString(" ", " ", "")
          }
        print("data " + typeName(t) + " " + tvars(t).mkString(" ")) 
        print(conslist.mkString(" = ", " | ", ""))
        
        if(fmt == "hipspec-total") {
          println(" deriving (Eq, Ord, Show, Typeable)")
          println()
          
          println("instance " + tvars(t).map("Show " + _).mkString("(",",",") => ") +
              "CoArbitrary (" + typeName(t) + " " + tvars(t).mkString(" ") + ") where")
          println("  coarbitrary = coarbitraryShow")
          println()
          
          println("instance " + tvars(t).map("Arbitrary " + _).mkString("(",",",") => ") +
              "Arbitrary (" + typeName(t) + " " + tvars(t).mkString(" ") + ") where")
          println("  arbitrary = sized $ \\s -> do")
          if(t.exists(argtypes(_).isEmpty)) {
            println("    if s == 0 then")
            println("      elements " + 
                t.filter(argtypes(_).isEmpty).mkString("[",",","]"))
            println("    else do")
          }
          println("      x <- choose (0 :: Int, " + (t.size - 1) + ")")
          println("      case x of")
          for((c,i) <- t.zipWithIndex) {
            if(argtypes(c).isEmpty)
              println("        " + i + " -> Prelude.return " + c)
            else
              println("        " + i + " -> " + c + " <$> " + 
                  argtypes(c).map(_ => "resize (s `Prelude.div` 2) arbitrary").mkString(" <*> "))
          }
          println()
        }
        else    
          println()
      }
    }
    
    println()
    
    for((name,bs) <- prog.defs; body <- bs) {
      println(name + " = " + body + ";")
    }
    
    println()
    
    if(fmt == "hipspec" || fmt == "hipspec-total") {
      var propnum = 0
      for(PropEq(e1, e2) <- prog.prove) {
        println("prop_" + propnum + " " + (e1.freeVars ++ e2.freeVars).mkString(" ") + " = " +
            "(" + e1 + ") =:= (" + e2 + ")" )
        propnum += 1
      }
    }
    
  }
}