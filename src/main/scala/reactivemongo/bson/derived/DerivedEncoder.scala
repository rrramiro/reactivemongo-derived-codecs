package reactivemongo.bson
package derived

import shapeless._
import shapeless.labelled._

import scala.annotation.implicitNotFound

@implicitNotFound(
  "Unable to derive a BSON encoder for type ${A}. If it is a case class, check that all its fields can be encoded."
)
trait DerivedEncoder[A] extends BSONDocumentWriter[A]

object DerivedEncoder extends DerivedEncoderLowPriority {

  def apply[A](implicit encoder: DerivedEncoder[A]): BSONDocumentWriter[A] = encoder

  implicit val encodeCNil: DerivedEncoder[CNil] =
    new DerivedEncoder[CNil] {
      override def write(t: CNil): BSONDocument = sys.error("No BSON representation of CNil")
    }

  implicit val encodeHNil: DerivedEncoder[HNil] =
    new DerivedEncoder[HNil] {
      override def write(t: HNil): BSONDocument = BSONDocument()
    }

  implicit def encodeCoproduct[K <: Symbol, L, R <: Coproduct](implicit
      typeName: Witness.Aux[K],
      encodeL: Lazy[BSONDocumentWriter[L]],
      encodeR: Lazy[DerivedEncoder[R]]
  ): DerivedEncoder[FieldType[K, L] :+: R] =
    new DerivedEncoder[FieldType[K, L] :+: R] {
      def write(t: FieldType[K, L] :+: R): BSONDocument = t match {
        case Inl(l) => BSONDocument(typeName.value.name -> encodeL.value.write(l))
        case Inr(r) => encodeR.value.write(r)
      }
    }

  implicit def encodeLabelledOptionHList[J <: BSONValue, K <: Symbol, H, T <: HList](implicit
      fieldName: Witness.Aux[K],
      encodeH: Lazy[BSONWriter[H, J]],
      encodeT: Lazy[DerivedEncoder[T]]
  ): DerivedEncoder[FieldType[K, Option[H]] :: T] =
    new DerivedEncoder[FieldType[K, Option[H]] :: T] {
      def write(l: FieldType[K, Option[H]] :: T): BSONDocument =
        BSONDocument(
          l.head.map(v => fieldName.value.name -> encodeH.value.write(v)).toSeq
        ) ++ encodeT.value.write(l.tail)
    }

}

trait DerivedEncoderLowPriority extends DerivedEncoderLowPriority0 {
  implicit def encodeLabelledHList[J <: BSONValue, K <: Symbol, H, T <: HList](implicit
      fieldName: Witness.Aux[K],
      encodeH: Lazy[BSONWriter[H, J]],
      encodeT: Lazy[DerivedEncoder[T]]
  ): DerivedEncoder[FieldType[K, H] :: T] =
    new DerivedEncoder[FieldType[K, H] :: T] {
      def write(l: FieldType[K, H] :: T): BSONDocument =
        BSONDocument(
          Seq(
            fieldName.value.name -> encodeH.value.write(l.head)
          )
        ) ++ encodeT.value.write(l.tail)
    }

}

trait DerivedEncoderLowPriority0 {

  // For convenience, automatically derive instances for coproduct types
  implicit def encodeCoproductDerived[K <: Symbol, L, R <: Coproduct](implicit
      typeName: Witness.Aux[K],
      encodeL: Lazy[DerivedEncoder[L]],
      encodeR: Lazy[DerivedEncoder[R]]
  ): DerivedEncoder[FieldType[K, L] :+: R] =
    new DerivedEncoder[FieldType[K, L] :+: R] {
      def write(t: FieldType[K, L] :+: R): BSONDocument = t match {
        case Inl(l) => BSONDocument(typeName.value.name -> encodeL.value.write(l))
        case Inr(r) => encodeR.value.write(r)
      }
    }

  implicit def encodeGeneric[A, R](implicit
      gen: LabelledGeneric.Aux[A, R],
      derivedEncoder: Lazy[DerivedEncoder[R]]
  ): DerivedEncoder[A] =
    new DerivedEncoder[A] {
      def write(a: A): BSONDocument = derivedEncoder.value.write(gen.to(a))
    }

}
