package play.db.jpa

import scala.collection.mutable
import JPQL.{instance => i}

/**
 *  provides a mini DSL for Model objects
 **/
trait QueryOn[T] {
  type M[T] = Manifest[T]

  implicit private def manifest2entity[T](m: M[T]): String = m.erasure.getName()

  /**
   * @return number of records
   **/
  def count()(implicit m: M[T]) = i.count(m)

  /**
   * count using a query
   * @param ps Array of params
   * @param query query
   * @param return number of records
   **/
  def count(q: String, ps: Any*)(implicit m: M[T]) = i.count(m, q, ps.asInstanceOf[Seq[AnyRef]].toArray)

  /**
   * return all records
   */
  def findAll(implicit m: M[T]) = i.all(m).fetch[T]

  /**
   * find records by Id
   * @param id id
   * @param return instance for the given id
   */
  def findById(id: Any)(implicit m: M[T]) = {
      i.findById(m, id) match {
          case x: AnyRef => Some(x.asInstanceOf[T])
          case _ => None
      }
  }

  /**
   * find a record based on a query
   * @param q query
   * @param ps Array of params
   * @param return a record based on the query and parameters
   */
  def findBy(q: String, ps: Any*)(implicit m: M[T]) = i.findBy(m, q, ps.asInstanceOf[Seq[AnyRef]].toArray)

  /**
   * this is the most generic finder which is also chainable (ie fetch, all, first etc. can be called on
   * the return type)
   * @param q query
   * @param ps parameters
   * @param return @see ScalaQuery
   */
  def find(q: String, ps: Any*)(implicit m: M[T]) = new ScalaQuery[T](i.find(m, q, ps.asInstanceOf[Seq[AnyRef]].toArray))

  /**
   * generic finder method that can be used with parameter bindings
   * @param q query
   * @param params parameters for bindings
   * @return ScalaQuery
   */
  def find(q: String, params: Map[String, Any])(implicit m: M[T]): ScalaQuery[T] = {
    val query = find(q)
    params.foreach {case (name, param) => query.bind(name, param)}
    query
  }

  /**
   * returns the wrapper object for all records
   */
  def all(implicit m: M[T]) = i.all(m)

  /**
   * deletes records based on thequery and parameters
   * @param q query
   * @param ps array of parameters
   */
  def delete(q: String, ps: Any*)(implicit m: M[T]) = i.delete(m, q, ps.asInstanceOf[Seq[AnyRef]].toArray)

  /**
   * deletes all records
   */
  def deleteAll(implicit m: M[T]) = i.deleteAll(m)

  /**
   * creates record for the given type T
   * @param name name
   * @param ps play scoped parameters
   * @param return T where T is the type of the current model
   */
  def create(name: String, ps: play.mvc.Scope.Params)(implicit m: M[T]): T = i.create(m, name, ps).asInstanceOf[T]
}

/**
 * provides Query functionality via companion object
 * it also caches QueryHolders
 **/
object QueryOn {
  private var queries = new mutable.HashMap[String, QueryHolder[_]]

  private def memoize[T](key: String): QueryHolder[T] = {
    if (!queries.contains(key)) {
      queries += (key) -> (new QueryHolder[T])
    }
    queries(key).asInstanceOf[QueryHolder[T]]
  }

  def apply[T](implicit m: scala.reflect.Manifest[T]): QueryHolder[T] = memoize[T](m.toString)
}


private[jpa] class QueryHolder[T] extends QueryOn[T]

private[jpa] class ScalaQuery[T](val query: JPASupport.JPAQuery) {

  def first(): Option[T] = {
      query.first().asInstanceOf[T] match {
          case x: AnyRef => Some(x.asInstanceOf[T])
          case _ => None
      }
  }

  def fetch() = asList[T](query.fetch())

  def all() = fetch()

  def fetch(size: Int) = asList[T](query.fetch(size))

  def from(offset: Int) = {
    query.from(offset)
    this
  }

  def bind(name: String, param: Any) = query.bind(name, param)

  private def asList[T](jlist: java.util.List[T]): List[T] = {
    import scala.collection.mutable.ListBuffer
    val buffer = ListBuffer[T]()
    for (e <- jlist.toArray) {
      buffer += e.asInstanceOf[T]
    }
    buffer.toList
  }
}
