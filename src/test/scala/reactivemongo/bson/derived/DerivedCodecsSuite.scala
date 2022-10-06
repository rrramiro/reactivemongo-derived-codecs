package reactivemongo.bson
package derived

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers

import scala.util.{Failure, Try}

class DerivedCodecsSuite extends AnyFunSuite with Checkers {

  // Stolen from http://github.com/travisbrown/circe
  sealed trait RecursiveAdtExample
  case class BaseAdtExample(a: String) extends RecursiveAdtExample
  case class NestedAdtExample(r: RecursiveAdtExample) extends RecursiveAdtExample

  implicit val adtCodec: BSONDocumentHandler[RecursiveAdtExample] = codec[RecursiveAdtExample]

  private def atDepth(depth: Int): Gen[RecursiveAdtExample] = if (depth < 3)
    Gen.oneOf(
      Arbitrary.arbitrary[String].map(BaseAdtExample),
      atDepth(depth + 1).map(NestedAdtExample)
    )
  else Arbitrary.arbitrary[String].map(BaseAdtExample)

  implicit val arbitraryRecursiveAdtExample: Arbitrary[RecursiveAdtExample] =
    Arbitrary(atDepth(0))

  test("identity") {
    check((v: RecursiveAdtExample) => adtCodec.read(adtCodec.write(v)) == v)
  }

  test("decoding failure raises a meaningful error message") {
    Try(adtCodec.read(BSONDocument())) match {
      case Failure(t) if t.getMessage == "Unable to decode coproduct" =>
      case _ =>
        fail()
    }
  }

}
