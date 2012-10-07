package graphsc
package residualization

trait LikenessMeasure[L] {
  def zero: L
  def infinity: L
  def combine(l: List[L]): L
}

object LikenessMeasure {
  implicit object IntLikenessMeasure extends LikenessMeasure[Int] {
    override def zero = 0
    override def infinity = Int.MaxValue
    override def combine(l: List[Int]) = 
      (infinity :: l).min match {
        case Int.MaxValue => Int.MaxValue
        case n => n + 1
      }
  }
}

case class LikenessCalculator[L](implicit lm: LikenessMeasure[L]) {
  import lm._
  
  implicit def injectOr(r: Option[Renaming]) = new {
    def |(l: Renaming): Option[Renaming] = r.flatMap(_ | l)
    def |(l: Option[Renaming]): Option[Renaming] = 
      for(x <- r; y <- l; k <- x | y) yield k 
  }
  
  def definingHyperedge(n: Node): Option[Hyperedge] = {
    val hypers = 
      n.outs.find(h => h.label match {
        case Construct(_) => true
        case Tick() => true
        case Var() => true
        case Error() => true
        case CaseOf(_) if h.dests(0).node.outs.exists(_.label == Var()) => true 
        case _ => false
      })
      
    assert(hypers.size <= 1)
    hypers.headOption
  }
  
  def likeness(
        l: RenamedNode, r: RenamedNode,
        hist: List[(Node, Node)] = Nil): Option[(L, Renaming)] = {
    likenessN(l.node, r.node, hist).map {
      case (i,ren) => 
        (i, l.renaming comp ren comp r.renaming.inv)
    }
  }
  
  def likenessN(
        l: Node, r: Node,
        hist: List[(Node, Node)] = Nil): Option[(L, Renaming)] = {
    val ldef = definingHyperedge(l)
    val rdef = definingHyperedge(r)
 
    if(l == r)
      Some((infinity, Renaming(r.used)))
    else if(ldef.isEmpty || rdef.isEmpty || hist.exists(p => p._1 == l || p._2 == r))
      Some((zero, Renaming()))
    else
      likenessH(ldef.get, rdef.get, hist)
  }  
  
  def likenessH(
        lh1: Hyperedge, rh1: Hyperedge,
        hist: List[(Node, Node)] = Nil): Option[(L, Renaming)] = {
    val ln = lh1.source.node
    val rn = rh1.source.node
    
    if(lh1.label != rh1.label || lh1.dests.size != rh1.dests.size)
      None
    else {
      lh1.label match {
        case Var() =>
          Some((infinity, lh1.source.renaming comp rh1.source.renaming.inv))
        case _ =>
          val lh = lh1.source.renaming.inv.compDests(lh1)
          val rh = rh1.source.renaming.inv.compDests(rh1)
          val chld = (lh.dests,rh.dests).zipped.map(likeness(_, _, (ln,rn) :: hist))
          
          if(!chld.forall(_.isDefined))
            None
          else {
            val shifts = lh.shifts
            val shift_rens = shifts.map(n => Renaming(0 until n toSet))
            
            val rens = 
              (chld.map(_.get._2), shift_rens, shifts).zipped.map(
                  (a,b,n) => (a | b).map(_.unshift(n)))
            
            val resren = 
              (Some(Renaming()).asInstanceOf[Option[Renaming]] /: rens)(_ | _)
            
            resren.map((combine(chld.map(_.get._1)), _))
          }
      } 
    }
  }
}