package graphsc

// Just supports named nodes
trait NamedNodes extends Hypergraph {
  val namedNodes = collection.mutable.Map[String, RenamedNode]()
  
  def apply(n: String): RenamedNode = namedNodes(n).deref
  
  def newNode(n: String, arity: Int): RenamedNode = 
    if(namedNodes.contains(n)) {
      namedNodes(n).deref
    }
    else {
      val node = newNode(0 until arity toSet)
      namedNodes += n -> node
      node.deref
    }
}

// Prettifies nodes on the fly
trait Prettifier extends TheHypergraph with NamedNodes {
  val prettyMap = collection.mutable.Map[Node, String]() 
  
  def pretty(n: Node): String = prettyMap.get(n) match {
    case None =>
      throw new NoSuchElementException("Node " + n + " is not pretty")
    case Some(s) => s
  }
  
  def pretty(n_underef: RenamedNode): String = {
    val RenamedNode(r, n) = n_underef.deref
    prettyRename(r, pretty(n))
  }
  
  def prettySet(n: Node, s: String) {
    prettyMap += n -> s
    n.prettyDebug = s
  }
  
  override def newNode(n: String, arity: Int): RenamedNode = {
    val node = super.newNode(n, arity)
    prettySet(node.node, n + (0 until arity).map("v" + _ + "v").mkString(" ", " ", ""))
    node
  }
  
  override def onNewHyperedge(h: Hyperedge) {
    try {
      val s = prettyRename(h.source.renaming.inv, prettyHyperedge(h))
      prettyMap.get(h.source.node) match {
        case Some(p) if p.length <= s.length =>
        case _ =>
          prettySet(h.source.node, s)
      }
    } catch {
      case _: NoSuchElementException =>
    }
    super.onNewHyperedge(h)
  }
  
  override def beforeGlue(l: RenamedNode, r: Node) {
    val lp = prettyMap.get(l.node)
    val rp = prettyMap.get(r).map(prettyRename(l.renaming.inv, _))
    assert(lp != None || rp != None)
    if(lp == None || (rp != None && rp.get.length < lp.get.length)) {
      prettySet(l.node, rp.get)
    }
    super.beforeGlue(l, r)
  }
  
  private def indent(s: String, ind: String = "  "): String = "  " + indent1(s, ind)
  private def indent1(s: String, ind: String = "  "): String = s.replace("\n", "\n" + ind)
  
  def prettyHyperedge(h: Hyperedge, prettyfun: RenamedNode => String = pretty _): String = 
    h.label match {
      case Construct(name) => name + " " + h.dests.map("(" + prettyfun(_) + ")").mkString(" ")
      case CaseOf(cases) =>
        "case " + prettyfun(h.dests(0)) + " of {\n" +
        indent((
          for(((n,k),e) <- cases zip h.dests.tail) yield
            n + " " + (0 until k map (i => "c" + (i + e.arity - k) + "c")).mkString(" ") + " -> " +
            indent1(prettyUnshift(k, prettyfun(e)))
        ).mkString(";\n")) + "\n}"
      case Let() =>
        val vars = h.dests.tail.zipWithIndex.map {
          case (e,i) => "b" + i + "b = " + indent1(prettyfun(e), "      ")
        }
        val in = indent1(prettyfun(h.dests(0)), "   ")
        "let\n" + indent(vars.mkString(";\n"), "  ") + "\nin " + 
        in.replaceAll("v([0-9]+)v", "b$1b")
      case Var() => "v" + 0 + "v"
      case Id() => prettyfun(h.dests(0))
      case Tick() => "* " + prettyfun(h.dests(0))
      case Improvement() => ">= " + prettyfun(h.dests(0))
      case Error() => "_|_"
    }
  
  def prettyRename(r: Renaming, orig: String): String = {
    "v([0-9]+)v".r.replaceAllIn(orig, 
          { m =>
              val i = m.group(1).toInt
              if(r(i) < 0)
                "_|_"
              else
                "v" + r(i) + "v" })
  }
  
  def prettyUnshift(sh: Int, orig: String): String = {
    "v([0-9]+)v".r.replaceAllIn(orig, 
          { m =>
              val i = m.group(1).toInt
              if(i < sh)
                "c" + i + "c"
              else
                "v" + (i - sh) + "v" })
  }
                
  def prettyTwoHyperedges(h1: Hyperedge, h2: Hyperedge): String = {
    prettyHyperedge(h1, 
        (n =>
          if(n.node == h2.source.node)
            prettyRename(n.renaming comp h2.source.renaming.inv,
                prettyHyperedge(h2, (n => "{" + pretty(n) + "}")))
          else
            "{" + pretty(n) + "}"))
  }
  
  override def nodeDotLabel(n: Node): String =
    super.nodeDotLabel(n) + "\\l" +
    pretty(n).replace("\n", "\\l") + "\\l" +
    "\\l"
    
  def statistics() {
    val hyperedges = allNodes.toList.flatMap(n => n.ins ++ n.outs).toSet
    val nodes = allNodes.toList.map(n => n.arity + "\n" + pretty(n))
    val mostpop = nodes.groupBy(identity).mapValues(_.size).maxBy(_._2)
    println("Nodes: " + allNodes.size + " should be: " + nodes.distinct.size)
    println("Hyperedges: " + hyperedges.size)
    println("Most popular(" + mostpop._2 + "): arity " + mostpop._1 + "\n")
  }
}

