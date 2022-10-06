package reactivemongo.bson

package derived

import org.scalatest.funsuite.AnyFunSuite

class MongoDeriveOptionTest extends AnyFunSuite {

  case class Person(
      firstname: String,
      lastname: String,
      age: Option[Int]
  )

  test("derive") {
    val json = BSONDocument(
      "firstname" -> "test",
      "lastname" -> "test"
      // "age" -> BSONNull
    )

    val codecPerson = derived.codec[Person]
    val result1 = codecPerson.read(json)
    val result2 = codecPerson.write(Person("test", "test", None))

    println(result1)
    println(BSONDocument.pretty(result2))
  }
}
