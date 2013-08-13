package graphsc

case class Hyperedge(label: Label, source: RenamedNode, dests: List[RenamedNode]) {
  // if source is not invertible then this hyperedge reduces its used set
  // if dests are not invertible then they won't consume some variables
  // But non-invertible dests lead to problems during generalization,
  // so we used to require them to be invertible (the only exception is when we are gluing)
  // But, well, we'd better fix the problems during generalization, I think.
  //require(label == Id() || dests.forall(_.isInvertible))
  
  // A hyperedge should be read as a universally quantified statement
  // forall x y z . f(x,y) = C[g(x), h(y,z)]
  // TODO: Should something like this be allowed?
  // forall x y . f(x) = E[g(x,y), f(y)]
  // (note that there are two occurrences of y in the rhs)
  
  label match {
    case _:Id => require(dests.size == 1)
    case _:Tick => require(dests.size == 1)
    case _:Improvement => require(dests.size == 1)
    //case Renaming(vec) => require(dests.size == 1 && dests(0).arity <= vec.size)
    case _:Var => require(dests.size == 0)
    //case _:Let => require(dests.size >= 1 && dests(0).arity <= dests.size - 1)
    case CaseOf(cases) => require(cases.size == dests.size - 1)
    case _ =>
  }
  
  // We cache the hash code as it is used very often.
  override val hashCode: Int = scala.runtime.ScalaRunTime._hashCode(this);
  
  def arity: Int = (used + (-1)).max + 1
  
  def used: Set[Int] = label match {
    case Id() => dests(0).used
    case Tick() => dests(0).used
    case Improvement() => dests(0).used
    case Var() => Set(0)
    case Construct(_) => (Set[Int]() /: dests.map(_.used))(_ | _)
    case Let() => 
      (Set[Int]() /: dests(0).used.collect { 
          case i if i < dests.tail.size => dests.tail(i).used
        })(_ | _)
    case CaseOf(cases) =>
      (dests(0).used /: (dests.tail zip cases).map{ 
          case (d,(_,n)) => d.used.map(_ - n).filter(_ >= 0) })(_ | _)
    case Unused() => Set()
  }
  
  def shifts: List[Int] = label match {
    case CaseOf(cases) => 0 :: cases.map(_._2)
    case Let() => (-1) :: dests.tail.map(_ => 0)
    case _ => dests.map(_ => 0)
  }
  
  def asDummyNode: RenamedNode =
    (new Node(used)).deref
  
  // Returns the same hyperedge but with different source
  def from(newsrc: RenamedNode): Hyperedge =
    Hyperedge(label, newsrc, dests)
    
  def freeSource: Hyperedge =
   from((new FreeNode(used)).deref)
  
  // Replace a node in source and destination nodes
  def replace(old: Node, n: RenamedNode): Hyperedge = {
    val newsrc = 
      if(source.node == old) 
        source.renaming comp n
      else 
        source
        
    val newdst = 
      for(d <- dests)
        yield if(d.node == old) d.renaming comp n else d
        
    Hyperedge(label, newsrc, newdst)
  }
  
  // Dereference all glued nodes
  def deref: Hyperedge =
    Hyperedge(label, source.deref, dests.map(_.deref))
    
  override def toString =
    source + " -> " + label + " -> " + dests.mkString(" ")
}
