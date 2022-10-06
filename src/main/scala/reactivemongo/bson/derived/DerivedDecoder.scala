package reactivemongo.bson
package derived

import shapeless._
import shapeless.labelled._

import scala.annotation.implicitNotFound

/** @tparam A
  *   Decoded type
  */
@implicitNotFound(
  "Unable to derive a BSON decoder for type ${A}. If it is a case class, check that all its fields can be decoded."
)
trait DerivedDecoder[A] extends BSONDocumentReader[A]

/** As usual the derivation process is as follows:
  *   - let shapeless represent our type A in terms of Coproduct (if it is a sealed trait) or HList
  *     (if it is a case class) ;
  *   - define how to decode Coproducts and HLists using implicit definitions
  */
object DerivedDecoder extends DerivedDecoderLowPriority {

  def apply[A](implicit decoder: DerivedDecoder[A]): BSONDocumentReader[A] = decoder

  implicit val decodeCNil: DerivedDecoder[CNil] =
    new DerivedDecoder[CNil] {
      def read(bson: BSONDocument): CNil = sys.error(s"Unable to decode coproduct")
    }

  implicit val decodeHNil: DerivedDecoder[HNil] =
    new DerivedDecoder[HNil] {
      def read(bson: BSONDocument): HNil = HNil
    }

  implicit def decodeCoproduct[K <: Symbol, L, R <: Coproduct](implicit
      typeName: Witness.Aux[K],
      decodeL: Lazy[BSONDocumentReader[L]],
      decodeR: Lazy[DerivedDecoder[R]]
  ): DerivedDecoder[FieldType[K, L] :+: R] =
    new DerivedDecoder[FieldType[K, L] :+: R] {
      def read(bson: BSONDocument): FieldType[K, L] :+: R =
        bson
          .getAs(typeName.value.name)(decodeL.value)
          .fold[FieldType[K, L] :+: R](Inr(decodeR.value.read(bson)))(l => Inl(field(l)))
    }

  implicit def decodeLabelledHList[J <: BSONValue, K <: Symbol, H, T <: HList](implicit
      fieldName: Witness.Aux[K],
      decodeH: Lazy[BSONReader[J, H]],
      decodeT: Lazy[DerivedDecoder[T]]
  ): DerivedDecoder[FieldType[K, H] :: T] = {
    new DerivedDecoder[FieldType[K, H] :: T] {
      def read(bson: BSONDocument): FieldType[K, H] :: T = {
        field[K](
          bson
            .getAs[H](fieldName.value.name)(decodeH.value)
            .getOrElse(sys.error(s"Unable to decode field ${fieldName.value.name}"))
        ) :: decodeT.value.read(bson)
      }
    }
  }

  implicit def decodeLabelledOptionHList[J <: BSONValue, K <: Symbol, H, T <: HList](implicit
      fieldName: Witness.Aux[K],
      decodeH: Lazy[BSONReader[J, H]],
      decodeT: Lazy[DerivedDecoder[T]]
  ): DerivedDecoder[FieldType[K, Option[H]] :: T] = {
    new DerivedDecoder[FieldType[K, Option[H]] :: T] {
      private val optReader: BSONReader[J, Option[H]] = new BSONReader[J, Option[H]] {
        override def read(bson: J): Option[H] = decodeH.value.readOpt(bson)
      }

      def read(bson: BSONDocument): FieldType[K, Option[H]] :: T = {
        field[K](bson.getAs[Option[H]](fieldName.value.name)(optReader).flatten) :: decodeT.value
          .read(bson)
      }
    }
  }

}

trait DerivedDecoderLowPriority {

  // For convenience, automatically derive instances for coproduct types. The only difference with decodeCoproduct is the type of `decodeL`.
  implicit def decodeCoproductDerived[K <: Symbol, L, R <: Coproduct](implicit
      typeName: Witness.Aux[K],
      decodeL: Lazy[DerivedDecoder[L]],
      decodeR: Lazy[DerivedDecoder[R]]
  ): DerivedDecoder[FieldType[K, L] :+: R] =
    new DerivedDecoder[FieldType[K, L] :+: R] {
      def read(bson: BSONDocument): FieldType[K, L] :+: R =
        bson
          .getAs(typeName.value.name)(decodeL.value)
          .fold[FieldType[K, L] :+: R](Inr(decodeR.value.read(bson)))(l => Inl(field(l)))
    }

  implicit def decodeGeneric[A, R](implicit
      gen: LabelledGeneric.Aux[A, R],
      derivedDecoder: Lazy[DerivedDecoder[R]]
  ): DerivedDecoder[A] =
    new DerivedDecoder[A] {
      def read(bson: BSONDocument): A = gen.from(derivedDecoder.value.read(bson))
    }

}
