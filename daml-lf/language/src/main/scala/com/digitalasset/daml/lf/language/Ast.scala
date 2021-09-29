// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.lf.language

import com.daml.lf.data.Ref._
import com.daml.lf.data._

object Ast {
  //
  // Identifiers
  //

  /** Fully applied type constructor. */
  case class TypeConApp(tycon: TypeConName, args: ImmArray[Type]) {
    def pretty: String =
      args.foldLeft(TTyCon(tycon): Type) { case (arg, acc) => TApp(acc, arg) }.pretty
  }

  /* Expression variable name. */
  type ExprVarName = Name

  /* Type variable name. */
  type TypeVarName = Name

  /* Reference to a field in a record or variant. */
  type FieldName = Name

  /* Variant constructor name. */
  type VariantConName = Name

  /* Variant constructor name. */
  type EnumConName = Name

  /* Binding in a let/update/scenario block. */
  case class Binding(binder: Option[ExprVarName], typ: Type, bound: Expr)

  //
  // Expressions
  //

  sealed abstract class Expr extends Product with Serializable {

    /** Infix alias for repeated [[EApp]] application. */
    @inline final def eApp(arg: Expr, args: Expr*): EApp =
      (args foldLeft EApp(this, arg))(EApp)

    /** Infix alias for repeated [[ETyApp]] application. */
    @inline final def eTyApp(typ: Type, typs: Type*): ETyApp =
      (typs foldLeft ETyApp(this, typ))(ETyApp)
  }

  /** Reference to a variable in current lexical scope. */
  final case class EVar(value: ExprVarName) extends Expr

  /** Reference to a value definition. */
  final case class EVal(value: ValueRef) extends Expr

  /** Reference to a builtin function. */
  final case class EBuiltin(value: BuiltinFunction) extends Expr

  /** Primitive constructor, e.g. True, False or Unit. */
  final case class EPrimCon(value: PrimCon) extends Expr

  /** Primitive literal. */
  final case class EPrimLit(value: PrimLit) extends Expr

  /** Record construction. */
  final case class ERecCon(tycon: TypeConApp, fields: ImmArray[(FieldName, Expr)]) extends Expr

  /** Record projection. */
  final case class ERecProj(tycon: TypeConApp, field: FieldName, record: Expr) extends Expr

  /** Non-destructive record update. */
  final case class ERecUpd(tycon: TypeConApp, field: FieldName, record: Expr, update: Expr)
      extends Expr

  /** Variant construction. */
  final case class EVariantCon(tycon: TypeConApp, variant: VariantConName, arg: Expr) extends Expr

  /** Variant construction. */
  final case class EEnumCon(tyConName: TypeConName, con: EnumConName) extends Expr

  /** Struct construction. */
  final case class EStructCon(fields: ImmArray[(FieldName, Expr)]) extends Expr

  /** Struct projection. */
  final case class EStructProj(field: FieldName, struct: Expr) extends Expr {
    // The actual index is filled in by the type checker.
    private[lf] var fieldIndex: Option[Int] = None
  }

  /** Non-destructive struct update. */
  final case class EStructUpd(field: FieldName, struct: Expr, update: Expr) extends Expr {
    // The actual index is filled in by the type checker.
    private[lf] var fieldIndex: Option[Int] = None
  }

  /** Expression application. Function can be an abstraction or a builtin function. */
  final case class EApp(fun: Expr, arg: Expr) extends Expr

  /** Type application. */
  final case class ETyApp(expr: Expr, typ: Type) extends Expr

  /** Expression abstraction. */
  final case class EAbs(
      binder: (ExprVarName, Type),
      body: Expr,
      ref: Option[DefinitionRef], // The definition in which this abstraction is defined.
  ) extends Expr

  /** Type abstraction. */
  final case class ETyAbs(binder: (TypeVarName, Kind), body: Expr) extends Expr

  /** Pattern matching. */
  final case class ECase(scrut: Expr, alts: ImmArray[CaseAlt]) extends Expr

  /** Let binding. */
  final case class ELet(binding: Binding, body: Expr) extends Expr

  /** Empty list constructor. */
  final case class ENil(typ: Type) extends Expr

  /** List construction. */
  final case class ECons(typ: Type, front: ImmArray[Expr], tail: Expr) extends Expr

  /** Update expression */
  final case class EUpdate(update: Update) extends Expr

  /** Scenario expression */
  final case class EScenario(scenario: Scenario) extends Expr

  /** Location annotations */
  final case class ELocation(loc: Location, expr: Expr) extends Expr

  final case class ENone(typ: Type) extends Expr

  final case class ESome(typ: Type, body: Expr) extends Expr

  /** Any constructor * */
  final case class EToAny(ty: Type, body: Expr) extends Expr

  /** Extract the underlying value if it matches the ty * */
  final case class EFromAny(ty: Type, body: Expr) extends Expr

  /** Unique textual representation of template Id * */
  final case class ETypeRep(typ: Type) extends Expr

  /** Throw an exception */
  final case class EThrow(returnType: Type, exceptionType: Type, exception: Expr) extends Expr

  /** Construct an AnyException from its message and payload */
  final case class EToAnyException(typ: Type, value: Expr) extends Expr

  /** Extract the payload from an AnyException if it matches the given exception type */
  final case class EFromAnyException(typ: Type, value: Expr) extends Expr

  /** Convert template payload to interface it implements */
  final case class EToInterface(iface: TypeConName, tpl: TypeConName, value: Expr) extends Expr

  /** Convert interface back to template payload if possible */
  final case class EFromInterface(iface: TypeConName, tpl: TypeConName, value: Expr) extends Expr

  /** Invoke an interface method */
  final case class ECallInterface(iface: TypeConName, method: MethodName, value: Expr) extends Expr

  //
  // Kinds
  //

  sealed abstract class Kind extends Product with Serializable {
    def pretty: String = Kind.prettyKind(this)
  }

  object Kind {

    def prettyKind(kind: Kind, needParens: Boolean = false): String = kind match {
      case KStar => "*"
      case KNat => "nat"
      case KArrow(fun, arg) if needParens =>
        "(" + prettyKind(fun, true) + "->" + prettyKind(arg, false) +
          ")"
      case KArrow(fun, arg) =>
        prettyKind(fun, true) + "->" + prettyKind(arg, false)
    }
  }

  /** Kind of a proper data type. */
  case object KStar extends Kind

  /** Kind of nat tye */
  case object KNat extends Kind

  /** Kind of higher kinded type. */
  final case class KArrow(param: Kind, result: Kind) extends Kind

  //
  // Types
  //

  // Note that we rely on the equality of Type so this must stay sealed
  // and all inhabitants should be case classes or case objects.

  sealed abstract class Type extends Product with Serializable {
    def pretty: String = Type.prettyType(this)
  }

  object Type {

    def prettyType(typ: Type): String = {
      val precTApp = 2
      val precTFun = 1
      val precTForall = 0

      def maybeParens(needParens: Boolean, s: String): String =
        if (needParens) s"($s)" else s

      def prettyType(t0: Type, prec: Int = precTForall): String = t0 match {
        case TVar(n) => n
        case TNat(n) => n.toString
        case TSynApp(syn, args) =>
          maybeParens(
            prec > precTApp,
            syn.qualifiedName.name.toString + " " +
              args
                .map { t =>
                  prettyType(t, precTApp + 1)
                }
                .toSeq
                .mkString(" "),
          )
        case TTyCon(con) => con.qualifiedName.name.toString
        case TBuiltin(BTArrow) => "(->)"
        case TBuiltin(bt) => bt.toString.stripPrefix("BT")
        case TApp(TApp(TBuiltin(BTArrow), param), result) =>
          maybeParens(
            prec > precTFun,
            prettyType(param, precTFun + 1) + " → " + prettyType(result, precTFun),
          )
        case TApp(fun, arg) =>
          maybeParens(
            prec > precTApp,
            prettyType(fun, precTApp) + " " + prettyType(arg, precTApp + 1),
          )
        case TForall((v, _), body) =>
          maybeParens(prec > precTForall, "∀" + v + prettyForAll(body))
        case TStruct(fields) =>
          "(" + fields.iterator
            .map { case (n, t) => n + ": " + prettyType(t, precTForall) }
            .toSeq
            .mkString(", ") + ")"
      }

      def prettyForAll(t: Type): String = t match {
        case TForall((v, _), body) => " " + v + prettyForAll(body)
        case _ => ". " + prettyType(t, precTForall)
      }

      prettyType(typ)
    }
  }

  /** Reference to a type variable. */
  final case class TVar(name: TypeVarName) extends Type

  /** nat type */
  // for now it can contains only a Numeric Scale
  final case class TNat(n: Numeric.Scale) extends Type

  object TNat {
    // works because Numeric.Scale.MinValue = 0
    val values = Numeric.Scale.values.map(new TNat(_))
    def apply(n: Numeric.Scale): TNat = values(n)
    val Decimal: TNat = values(10)
  }

  /** Fully applied type synonym. */
  final case class TSynApp(tysyn: TypeSynName, args: ImmArray[Type]) extends Type

  /** Reference to a type constructor. */
  final case class TTyCon(tycon: TypeConName) extends Type

  /** Reference to builtin type. */
  final case class TBuiltin(bt: BuiltinType) extends Type

  /** Application of a type function to a type. */
  final case class TApp(tyfun: Type, arg: Type) extends Type

  /** Universally quantified type. */
  final case class TForall(binder: (TypeVarName, Kind), body: Type) extends Type

  /** Structs */
  final case class TStruct(fields: Struct[Type]) extends Type

  sealed abstract class BuiltinType extends Product with Serializable

  case object BTInt64 extends BuiltinType
  case object BTNumeric extends BuiltinType
  case object BTText extends BuiltinType
  case object BTTimestamp extends BuiltinType
  case object BTParty extends BuiltinType
  case object BTUnit extends BuiltinType
  case object BTBool extends BuiltinType
  case object BTList extends BuiltinType
  case object BTOptional extends BuiltinType
  case object BTTextMap extends BuiltinType
  case object BTGenMap extends BuiltinType
  case object BTUpdate extends BuiltinType
  case object BTScenario extends BuiltinType
  case object BTDate extends BuiltinType
  case object BTContractId extends BuiltinType
  case object BTArrow extends BuiltinType
  case object BTAny extends BuiltinType
  case object BTTypeRep extends BuiltinType
  case object BTAnyException extends BuiltinType
  case object BTRoundingMode extends BuiltinType
  case object BTBigNumeric extends BuiltinType

  //
  // Primitive literals
  //

  sealed abstract class PrimLit extends Equals with Product with Serializable {
    def value: Any
  }

  final case class PLInt64(override val value: Long) extends PrimLit
  final case class PLNumeric(override val value: Numeric) extends PrimLit
  // Text should be treated as Utf8, data.Utf8 provide emulation functions for that
  final case class PLText(override val value: String) extends PrimLit
  final case class PLTimestamp(override val value: Time.Timestamp) extends PrimLit
  final case class PLParty(override val value: Party) extends PrimLit
  final case class PLDate(override val value: Time.Date) extends PrimLit
  final case class PLRoundingMode(override val value: java.math.RoundingMode) extends PrimLit

  //
  // Primitive constructors
  //

  sealed abstract class PrimCon extends Product with Serializable

  case object PCTrue extends PrimCon
  case object PCFalse extends PrimCon
  case object PCUnit extends PrimCon

  //
  // Builtin functions.
  //

  sealed abstract class BuiltinFunction extends Product with Serializable

  final case object BTrace extends BuiltinFunction // : ∀a. Text -> a -> a

  // Numeric arithmetic
  final case object BAddNumeric extends BuiltinFunction // :  ∀s. Numeric s → Numeric s → Numeric s
  final case object BSubNumeric extends BuiltinFunction // :  ∀s. Numeric s → Numeric s → Numeric s
  final case object BMulNumeric
      extends BuiltinFunction // :  ∀s1 s2 s. Numeric s1 → Numeric s2 → Numeric s
  final case object BDivNumeric
      extends BuiltinFunction // :  ∀s1 s2 s. Numeric s1 → Numeric s2 → Numeric s
  final case object BRoundNumeric extends BuiltinFunction // :  ∀s. Integer → Numeric s → Numeric s
  final case object BCastNumeric extends BuiltinFunction // : ∀s1 s2. Numeric s1 → Numeric s2
  final case object BShiftNumeric extends BuiltinFunction // : ∀s1 s2. Numeric s1 → Numeric s2

  // Int64 arithmetic
  final case object BAddInt64 extends BuiltinFunction // : Int64 → Int64 → Int64
  final case object BSubInt64 extends BuiltinFunction // : Int64 → Int64 → Int64
  final case object BMulInt64 extends BuiltinFunction // : Int64 → Int64 → Int64
  final case object BDivInt64 extends BuiltinFunction // : Int64 → Int64 → Int64
  final case object BModInt64 extends BuiltinFunction // : Int64 → Int64 → Int64
  final case object BExpInt64 extends BuiltinFunction // : Int64 → Int64 → Int64

  // Conversions
  final case object BInt64ToNumeric extends BuiltinFunction // : ∀s. Int64 → Numeric s
  final case object BNumericToInt64 extends BuiltinFunction // : ∀s. Numeric s → Int64
  final case object BDateToUnixDays extends BuiltinFunction // : Date -> Int64
  final case object BUnixDaysToDate extends BuiltinFunction // : Int64 -> Date
  final case object BTimestampToUnixMicroseconds extends BuiltinFunction // : Timestamp -> Int64
  final case object BUnixMicrosecondsToTimestamp extends BuiltinFunction // : Int64 -> Timestamp

  // Folds
  final case object BFoldl extends BuiltinFunction // : ∀a b. (b → a → b) → b → List a → b
  final case object BFoldr extends BuiltinFunction // : ∀a b. (a → b → b) → b → List a → b

  // Maps
  final case object BTextMapEmpty extends BuiltinFunction // : ∀ a. TextMap a
  final case object BTextMapInsert
      extends BuiltinFunction // : ∀ a. Text -> a -> TextMap a -> TextMap a
  final case object BTextMapLookup extends BuiltinFunction // : ∀ a. Text -> TextMap a -> Optional a
  final case object BTextMapDelete extends BuiltinFunction // : ∀ a. Text -> TextMap a -> TextMap a
  final case object BTextMapToList
      extends BuiltinFunction // : ∀ a. TextMap a -> [Struct("key":Text, "value":a)]
  final case object BTextMapSize extends BuiltinFunction // : ∀ a. TextMap a -> Int64

  // Generic Maps
  final case object BGenMapEmpty extends BuiltinFunction // : ∀ a b. GenMap a b
  final case object BGenMapInsert
      extends BuiltinFunction // : ∀ a b. a -> b -> GenMap a b -> GenMap a b
  final case object BGenMapLookup extends BuiltinFunction // : ∀ a b. a -> GenMap a b -> Optional b
  final case object BGenMapDelete extends BuiltinFunction // : ∀ a b. a -> GenMap a b -> GenMap a b
  final case object BGenMapKeys extends BuiltinFunction // : ∀ a b. GenMap a b -> [a]
  final case object BGenMapValues extends BuiltinFunction // : ∀ a b. GenMap a b -> [b]
  final case object BGenMapSize extends BuiltinFunction // : ∀ a b. GenMap a b -> Int64

  // Text functions
  final case object BExplodeText extends BuiltinFunction // : Text → List Char
  final case object BImplodeText extends BuiltinFunction // : List Text -> Text
  final case object BAppendText extends BuiltinFunction // : Text → Text → Text

  final case object BInt64ToText extends BuiltinFunction //  Int64 → Text
  final case object BNumericToText extends BuiltinFunction // : ∀s. Numeric s → Text
  final case object BTextToText extends BuiltinFunction // : Text → Text
  final case object BTimestampToText extends BuiltinFunction // : Timestamp → Text
  final case object BPartyToText extends BuiltinFunction // : Party → Text
  final case object BDateToText extends BuiltinFunction // : Date -> Text
  final case object BContractIdToText
      extends BuiltinFunction // : forall t. ContractId t -> Optional Text
  final case object BPartyToQuotedText extends BuiltinFunction // : Party -> Text
  final case object BCodePointsToText extends BuiltinFunction // : [Int64] -> Text
  final case object BTextToParty extends BuiltinFunction // : Text -> Optional Party
  final case object BTextToInt64 extends BuiltinFunction // : Text -> Optional Int64
  final case object BTextToNumeric extends BuiltinFunction // :  ∀s. Text -> Optional (Numeric s)
  final case object BTextToCodePoints extends BuiltinFunction // : Text -> List Int64

  final case object BSHA256Text extends BuiltinFunction // : Text -> Text

  // Errors
  final case object BError extends BuiltinFunction // : ∀a. Text → a

  // Comparisons
  final case object BLessNumeric extends BuiltinFunction // :  ∀s. Numeric s → Numeric s → Bool
  final case object BLessEqNumeric extends BuiltinFunction // :  ∀s. Numeric →  ∀s. Numeric → Bool
  final case object BGreaterNumeric extends BuiltinFunction // :  ∀s. Numeric s → Numeric s → Bool
  final case object BGreaterEqNumeric extends BuiltinFunction // : ∀s. Numeric s → Numeric s → Bool
  final case object BEqualNumeric
      extends BuiltinFunction // :  ∀s. Numeric s ->  ∀s. Numeric s -> Bool

  final case object BEqualList
      extends BuiltinFunction // : ∀a. (a -> a -> Bool) -> List a -> List a -> Bool
  final case object BEqualContractId
      extends BuiltinFunction // : ∀a. ContractId a -> ContractId a -> Bool
  final case object BEqual extends BuiltinFunction // ∀a. a -> a -> Bool
  final case object BLess extends BuiltinFunction // ∀a. a -> a -> Bool
  final case object BLessEq extends BuiltinFunction // ∀a. a -> a -> Bool
  final case object BGreater extends BuiltinFunction // ∀a. a -> a -> Bool
  final case object BGreaterEq extends BuiltinFunction // ∀a. a -> a -> Bool

  final case object BCoerceContractId
      extends BuiltinFunction // : ∀a b. ContractId a -> ContractId b

  // Exceptions
  final case object BAnyExceptionMessage extends BuiltinFunction // AnyException → Text

  // Numeric arithmetic
  final case object BScaleBigNumeric extends BuiltinFunction // : BigNumeric → Int64
  final case object BPrecisionBigNumeric extends BuiltinFunction // : BigNumeric → Int64
  final case object BAddBigNumeric extends BuiltinFunction // : BigNumeric → BigNumeric → BigNumeric
  final case object BSubBigNumeric
      extends BuiltinFunction // :  BigNumeric → BigNumeric → BigNumeric
  final case object BMulBigNumeric extends BuiltinFunction // : BigNumeric → BigNumeric → BigNumeric
  final case object BDivBigNumeric
      extends BuiltinFunction // : Int64 -> RoundingMode → BigNumeric → BigNumeric → BigNumeric s
  final case object BShiftRightBigNumeric
      extends BuiltinFunction // : Int64 → BigNumeric → BigNumeric
  final case object BBigNumericToNumeric extends BuiltinFunction // :  ∀s. BigNumeric → Numeric s
  final case object BNumericToBigNumeric extends BuiltinFunction // :  ∀s. Numeric s → BigNumeric
  final case object BBigNumericToText extends BuiltinFunction // : BigNumeric → Text

  // Unstable Text Primitives
  final case object BTextToUpper extends BuiltinFunction // Text → Text
  final case object BTextToLower extends BuiltinFunction // : Text → Text
  final case object BTextSlice extends BuiltinFunction // : Int64 → Int64 → Text → Text
  final case object BTextSliceIndex extends BuiltinFunction // : Text → Text → Optional Int64
  final case object BTextContainsOnly extends BuiltinFunction // : Text → Text → Bool
  final case object BTextReplicate extends BuiltinFunction // : Int64 → Text → Text
  final case object BTextSplitOn extends BuiltinFunction // : Text → Text → List Text
  final case object BTextIntercalate extends BuiltinFunction // : Text → List Text → Text

  final case class EExperimental(name: String, typ: Type) extends Expr

  //
  // Update expressions
  //

  case class RetrieveByKey(templateId: TypeConName, key: Expr)

  sealed abstract class Update extends Product with Serializable

  final case class UpdatePure(t: Type, expr: Expr) extends Update
  final case class UpdateBlock(bindings: ImmArray[Binding], body: Expr) extends Update
  final case class UpdateCreate(templateId: TypeConName, arg: Expr) extends Update
  final case class UpdateFetch(templateId: TypeConName, contractId: Expr) extends Update
  final case class UpdateFetchInterface(interface: TypeConName, contractId: Expr) extends Update
  final case class UpdateExercise(
      templateId: TypeConName,
      choice: ChoiceName,
      cidE: Expr,
      argE: Expr,
  ) extends Update
  final case class UpdateExerciseInterface(
      interface: TypeConName,
      choice: ChoiceName,
      cidE: Expr,
      argE: Expr,
  ) extends Update
  final case class UpdateExerciseByKey(
      templateId: TypeConName,
      choice: ChoiceName,
      keyE: Expr,
      argE: Expr,
  ) extends Update
  case object UpdateGetTime extends Update
  final case class UpdateFetchByKey(rbk: RetrieveByKey) extends Update
  final case class UpdateLookupByKey(rbk: RetrieveByKey) extends Update
  final case class UpdateEmbedExpr(typ: Type, body: Expr) extends Update
  final case class UpdateTryCatch(
      typ: Type,
      body: Expr,
      binder: ExprVarName,
      handler: Expr,
  ) extends Update

  //
  // Scenario expressions
  //

  sealed abstract class Scenario extends Product with Serializable

  final case class ScenarioPure(t: Type, expr: Expr) extends Scenario
  final case class ScenarioBlock(bindings: ImmArray[Binding], body: Expr) extends Scenario
  final case class ScenarioCommit(partyE: Expr, updateE: Expr, retType: Type) extends Scenario
  final case class ScenarioMustFailAt(partyE: Expr, updateE: Expr, retType: Type) extends Scenario
  final case class ScenarioPass(relTimeE: Expr) extends Scenario
  case object ScenarioGetTime extends Scenario
  final case class ScenarioGetParty(nameE: Expr) extends Scenario
  final case class ScenarioEmbedExpr(typ: Type, body: Expr) extends Scenario

  //
  // Pattern matching
  //

  sealed abstract class CasePat extends Product with Serializable

  // Match on variant
  final case class CPVariant(tycon: TypeConName, variant: VariantConName, binder: ExprVarName)
      extends CasePat
  // Match on enum
  final case class CPEnum(tycon: TypeConName, constructor: EnumConName) extends CasePat
  // Match on primitive constructor.
  final case class CPPrimCon(pc: PrimCon) extends CasePat
  // Match on an empty list.
  case object CPNil extends CasePat
  // Match on a non-empty list.
  final case class CPCons(head: ExprVarName, tail: ExprVarName) extends CasePat
  // Match on anything. Should be the last alternative.
  case object CPDefault extends CasePat
  // match on none
  case object CPNone extends CasePat
  // match on some
  final case class CPSome(body: ExprVarName) extends CasePat

  // Case alternative
  case class CaseAlt(pattern: CasePat, expr: Expr)

  //
  // Definitions
  //

  sealed abstract class GenDefinition[+E] extends Product with Serializable

  final case class DTypeSyn(params: ImmArray[(TypeVarName, Kind)], typ: Type)
      extends GenDefinition[Nothing]
  final case class DDataType(
      serializable: Boolean,
      params: ImmArray[(TypeVarName, Kind)],
      cons: DataCons,
  ) extends GenDefinition[Nothing]
  object DDataType {
    val Interface = DDataType(true, ImmArray.empty, DataInterface)
  }

  final case class GenDValue[E](
      typ: Type,
      noPartyLiterals: Boolean,
      body: E,
      isTest: Boolean,
  ) extends GenDefinition[E]

  class GenDValueCompanion[E] {
    def apply(typ: Type, noPartyLiterals: Boolean, body: E, isTest: Boolean): GenDValue[E] =
      new GenDValue(typ, noPartyLiterals, body, isTest)

    def unapply(arg: GenDValue[E]): Some[(Type, Boolean, E, Boolean)] =
      Some((arg.typ, arg.noPartyLiterals, arg.body, arg.isTest))
  }

  type DValue = GenDValue[Expr]
  object DValue extends GenDValueCompanion[Expr]
  type DValueSignature = GenDValue[Unit]
  object DValueSignature extends GenDValueCompanion[Unit]

  type Definition = GenDefinition[Expr]
  type DefinitionSignature = GenDefinition[Unit]

  // Data constructor in data type definition.
  sealed abstract class DataCons extends Product with Serializable

  final case class DataRecord(fields: ImmArray[(FieldName, Type)]) extends DataCons {
    lazy val fieldInfo: Map[FieldName, (Type, Int)] =
      fields.iterator.zipWithIndex.map { case ((field, typ), rank) => (field, (typ, rank)) }.toMap
  }
  final case class DataVariant(variants: ImmArray[(VariantConName, Type)]) extends DataCons {
    lazy val constructorInfo: Map[VariantConName, (Type, Int)] =
      variants.iterator.zipWithIndex.map { case ((cons, typ), rank) => (cons, (typ, rank)) }.toMap
    variants.iterator.zipWithIndex.map { case ((cons, typ), rank) => (cons, (typ, rank)) }.toMap
  }
  final case class DataEnum(constructors: ImmArray[EnumConName]) extends DataCons {
    lazy val constructorRank: Map[EnumConName, Int] = constructors.iterator.zipWithIndex.toMap
  }
  case object DataInterface extends DataCons

  final case class GenTemplateKey[E](
      typ: Type,
      body: E,
      // function from key type to [Party]
      maintainers: E,
  )

  sealed class GenTemplateKeyCompanion[E] {
    def apply(typ: Type, body: E, maintainers: E): GenTemplateKey[E] =
      new GenTemplateKey(typ, body, maintainers)

    def unapply(arg: GenTemplateKey[E]): Some[(Type, E, E)] =
      Some((arg.typ, arg.body, arg.maintainers))
  }

  type TemplateKey = GenTemplateKey[Expr]
  object TemplateKey extends GenTemplateKeyCompanion[Expr]

  type TemplateKeySignature = GenTemplateKey[Unit]
  object TemplateKeySignature extends GenTemplateKeyCompanion[Unit]

  final case class DefInterface(
      choices: Map[ChoiceName, InterfaceChoice],
      methods: Map[MethodName, InterfaceMethod],
  )

  object DefInterface {
    def apply(
        choices: Iterable[(ChoiceName, InterfaceChoice)],
        methods: Iterable[(MethodName, InterfaceMethod)],
    ): DefInterface = {
      val choiceMap = toMapWithoutDuplicate(
        choices,
        (name: ChoiceName) =>
          throw PackageError(s"collision on interface choice name ${name.toString}"),
      )
      val methodMap = toMapWithoutDuplicate(
        methods,
        (name: MethodName) =>
          throw PackageError(s"collision on interface method name ${name.toString}"),
      )
      DefInterface(choiceMap, methodMap)
    }
  }

  case class InterfaceChoice(
      name: ChoiceName,
      consuming: Boolean,
      argType: Type,
      returnType: Type,
      // TODO interfaces Should observers or controllers be part of the interface?
  )

  case class InterfaceMethod(
      name: MethodName,
      returnType: Type,
  )

  case class GenTemplate[E] private[Ast] (
      param: ExprVarName, // Binder for template argument.
      precond: E, // Template creation precondition.
      signatories: E, // Parties agreeing to the contract.
      agreementText: E, // Text the parties agree to.
      choices: Map[ChoiceName, GenTemplateChoice[E]], // Choices available in the template.
      observers: E, // Observers of the contract.
      key: Option[GenTemplateKey[E]],
      implements: Map[TypeConName, GenTemplateImplements[E]],
  ) extends NoCopy

  sealed class GenTemplateCompanion[E] {

    def apply(
        param: ExprVarName,
        precond: E,
        signatories: E,
        agreementText: E,
        choices: Iterable[(ChoiceName, GenTemplateChoice[E])],
        observers: E,
        key: Option[GenTemplateKey[E]],
        implements: Iterable[(TypeConName, GenTemplateImplements[E])],
    ): GenTemplate[E] = {

      val choiceMap = toMapWithoutDuplicate(
        choices,
        (choiceName: ChoiceName) =>
          throw PackageError(s"collision on choice name ${choiceName.toString}"),
      )

      val implementsMap = toMapWithoutDuplicate(
        implements,
        (ifaceName: TypeConName) =>
          throw PackageError(s"repeated interface implementation ${ifaceName.toString}"),
      )

      new GenTemplate[E](
        param,
        precond,
        signatories,
        agreementText,
        choiceMap,
        observers,
        key,
        implementsMap,
      )
    }

    def apply(arg: GenTemplate[E]): Option[
      (
          ExprVarName,
          E,
          E,
          E,
          Map[ChoiceName, GenTemplateChoice[E]],
          E,
          Option[GenTemplateKey[E]],
          Map[TypeConName, GenTemplateImplements[E]],
      )
    ] = GenTemplate.unapply(arg)

    def unapply(arg: GenTemplate[E]): Some[
      (
          ExprVarName,
          E,
          E,
          E,
          Map[ChoiceName, GenTemplateChoice[E]],
          E,
          Option[GenTemplateKey[E]],
          Map[TypeConName, GenTemplateImplements[E]],
      )
    ] = Some(
      (
        arg.param,
        arg.precond,
        arg.signatories,
        arg.agreementText,
        arg.choices,
        arg.observers,
        arg.key,
        arg.implements,
      )
    )
  }

  type Template = GenTemplate[Expr]
  object Template extends GenTemplateCompanion[Expr]

  type TemplateSignature = GenTemplate[Unit]
  object TemplateSignature extends GenTemplateCompanion[Unit]

  case class GenTemplateChoice[E](
      name: ChoiceName, // Name of the choice.
      consuming: Boolean, // Flag indicating whether exercising the choice consumes the contract.
      controllers: E, // Parties that can exercise the choice.
      choiceObservers: Option[E], // Additional informees for the choice.
      selfBinder: ExprVarName, // Self ContractId binder.
      argBinder: (ExprVarName, Type), // Choice argument binder.
      returnType: Type, // Return type of the choice follow-up.
      update: E, // The choice follow-up.
  )

  sealed class GenTemplateChoiceCompanion[E] {
    def apply(
        name: ChoiceName,
        consuming: Boolean,
        controllers: E,
        choiceObservers: Option[E],
        selfBinder: ExprVarName,
        argBinder: (ExprVarName, Type),
        returnType: Type,
        update: E,
    ): GenTemplateChoice[E] =
      GenTemplateChoice(
        name,
        consuming,
        controllers,
        choiceObservers,
        selfBinder,
        argBinder,
        returnType,
        update,
      )

    def unapply(
        arg: GenTemplateChoice[E]
    ): Some[(ChoiceName, Boolean, E, Option[E], ExprVarName, (ExprVarName, Type), Type, E)] =
      Some(
        (
          arg.name,
          arg.consuming,
          arg.controllers,
          arg.choiceObservers,
          arg.selfBinder,
          arg.argBinder,
          arg.returnType,
          arg.update,
        )
      )
  }

  type TemplateChoice = GenTemplateChoice[Expr]
  object TemplateChoice extends GenTemplateChoiceCompanion[Expr]

  type TemplateChoiceSignature = GenTemplateChoice[Unit]
  object TemplateChoiceSignature extends GenTemplateChoiceCompanion[Unit]

  case class GenTemplateImplements[E] private[Ast] (
      interface: TypeConName,
      methods: Map[MethodName, GenTemplateImplementsMethod[E]],
  )

  sealed class GenTemplateImplementsCompanion[E] {
    def apply(
        interface: TypeConName,
        methods: Iterable[(MethodName, GenTemplateImplementsMethod[E])],
    ): GenTemplateImplements[E] = {
      val methodMap = toMapWithoutDuplicate(
        methods,
        (methodName: MethodName) =>
          throw PackageError(s"repeated method implementation ${methodName.toString}"),
      )
      new GenTemplateImplements[E](interface, methodMap)
    }

    def apply(
        interface: TypeConName,
        methods: Map[MethodName, GenTemplateImplementsMethod[E]],
    ): GenTemplateImplements[E] =
      GenTemplateImplements[E](interface, methods)

    def unapply(
        arg: GenTemplateImplements[E]
    ): Some[(TypeConName, Map[MethodName, GenTemplateImplementsMethod[E]])] =
      Some((arg.interface, arg.methods))
  }

  type TemplateImplements = GenTemplateImplements[Expr]
  object TemplateImplements extends GenTemplateImplementsCompanion[Expr]

  type TemplateImplementsSignature = GenTemplateImplements[Unit]
  object TemplateImplementsSignature extends GenTemplateImplementsCompanion[Unit]

  case class GenTemplateImplementsMethod[E] private[Ast] (
      name: MethodName,
      value: E,
  )

  sealed class GenTemplateImplementsMethodCompanion[E] {
    def apply(
        name: MethodName,
        value: E,
    ): GenTemplateImplementsMethod[E] =
      GenTemplateImplementsMethod[E](name, value)

    def unapply(
        arg: GenTemplateImplementsMethod[E]
    ): Some[(MethodName, E)] =
      Some((arg.name, arg.value))
  }

  type TemplateImplementsMethod = GenTemplateImplementsMethod[Expr]
  object TemplateImplementsMethod extends GenTemplateImplementsMethodCompanion[Expr]

  type TemplateImplementsMethodSignature = GenTemplateImplementsMethod[Unit]
  object TemplateImplementsMethodSignature extends GenTemplateImplementsMethodCompanion[Unit]

  final case class GenDefException[E](message: E)

  sealed class GenDefExceptionCompanion[E] {
    def apply(message: E): GenDefException[E] =
      GenDefException(message)

    def unapply(arg: GenDefException[E]): Some[E] =
      Some((arg.message))
  }

  type DefException = GenDefException[Expr]
  object DefException extends GenDefExceptionCompanion[Expr]

  type DefExceptionSignature = GenDefException[Unit]
  val DefExceptionSignature = GenDefException(())

  case class FeatureFlags(
      forbidPartyLiterals: Boolean // If set to true, party literals are not allowed to appear in daml-lf packages.
      /*
      These flags are present in Daml-LF, but our ecosystem does not support them anymore:
      dontDivulgeContractIdsInCreateArguments: Boolean, // If set to true, arguments to creates are not divulged.
      // Instead target contract id's of exercises are divulged
      // and fetches are authorized.
      dontDiscloseNonConsumingChoicesToObservers: Boolean // If set to true, exercise nodes of
      // non-consuming choices are only
      // disclosed to the signatories and
      // controllers of the target contract/choice
      // and not to the observers of the target contract.
       */
  )

  object FeatureFlags {
    val default = FeatureFlags(
      forbidPartyLiterals = false
    )
  }

  //
  // Modules and packages
  //

  case class GenModule[E] private[Ast] (
      name: ModuleName,
      definitions: Map[DottedName, GenDefinition[E]],
      templates: Map[DottedName, GenTemplate[E]],
      exceptions: Map[DottedName, GenDefException[E]],
      interfaces: Map[DottedName, DefInterface],
      featureFlags: FeatureFlags,
  ) extends NoCopy

  private[this] def toMapWithoutDuplicate[Key, Value](
      xs: Iterable[(Key, Value)],
      error: Key => Nothing,
  ): Map[Key, Value] =
    xs.foldLeft[Map[Key, Value]](Map.empty[Key, Value]) { case (acc, (key, value)) =>
      if (acc.contains(key)) {
        error(key)
      } else {
        acc.updated(key, value)
      }
    }

  sealed class GenModuleCompanion[E] {

    def apply(
        name: ModuleName,
        definitions: Iterable[(DottedName, GenDefinition[E])],
        templates: Iterable[(DottedName, GenTemplate[E])],
        exceptions: Iterable[(DottedName, GenDefException[E])],
        interfaces: Iterable[(DottedName, DefInterface)],
        featureFlags: FeatureFlags,
    ): GenModule[E] = {

      val definitionMap =
        toMapWithoutDuplicate(
          definitions,
          (name: DottedName) => throw PackageError(s"Collision on definition name ${name.toString}"),
        )

      val templateMap =
        toMapWithoutDuplicate(
          templates,
          (name: DottedName) => throw PackageError(s"Collision on template name ${name.toString}"),
        )

      val exceptionMap =
        toMapWithoutDuplicate(
          exceptions,
          (name: DottedName) => throw PackageError(s"Collision on exception name ${name.toString}"),
        )

      val interfaceMap =
        toMapWithoutDuplicate(
          interfaces,
          (name: DottedName) => throw PackageError(s"Collision on interface name ${name.toString}"),
        )

      templateMap.keysIterator.find(exceptionMap.keySet.contains).foreach { name =>
        throw PackageError(s"Collision between exception and template name ${name.toString}")
      }

      GenModule(name, definitionMap, templateMap, exceptionMap, interfaceMap, featureFlags)
    }

    def unapply(arg: GenModule[E]): Some[
      (
          ModuleName,
          Map[DottedName, GenDefinition[E]],
          Map[DottedName, GenTemplate[E]],
          Map[DottedName, GenDefException[E]],
          Map[DottedName, DefInterface],
          FeatureFlags,
      )
    ] = Some(
      (arg.name, arg.definitions, arg.templates, arg.exceptions, arg.interfaces, arg.featureFlags)
    )
  }

  type Module = GenModule[Expr]
  object Module extends GenModuleCompanion[Expr]

  type ModuleSignature = GenModule[Unit]
  object ModuleSignature extends GenModuleCompanion[Unit]

  case class PackageMetadata(name: PackageName, version: PackageVersion)

  case class GenPackage[E](
      modules: Map[ModuleName, GenModule[E]],
      directDeps: Set[PackageId],
      languageVersion: LanguageVersion,
      metadata: Option[PackageMetadata],
  )

  sealed class GenPackageCompanion[E] {

    def apply(
        modules: Iterable[GenModule[E]],
        directDeps: Iterable[PackageId],
        languageVersion: LanguageVersion,
        metadata: Option[PackageMetadata],
    ): GenPackage[E] = {
      val modulesWithNames = modules.map(m => m.name -> m)
      val moduleMap =
        toMapWithoutDuplicate(
          modulesWithNames,
          (modName: ModuleName) =>
            throw PackageError(s"Collision on module name ${modName.toString}"),
        )
      this(moduleMap, directDeps.toSet, languageVersion, metadata)
    }

    def apply(
        modules: Map[ModuleName, GenModule[E]],
        directDeps: Set[PackageId],
        languageVersion: LanguageVersion,
        metadata: Option[PackageMetadata],
    ) =
      GenPackage(
        modules: Map[ModuleName, GenModule[E]],
        directDeps: Set[PackageId],
        languageVersion: LanguageVersion,
        metadata: Option[PackageMetadata],
      )

    def unapply(arg: GenPackage[E]): Some[
      (
          Map[ModuleName, GenModule[E]],
          Set[PackageId],
          LanguageVersion,
          Option[PackageMetadata],
      )
    ] = Some((arg.modules, arg.directDeps, arg.languageVersion, arg.metadata))
  }

  type Package = GenPackage[Expr]
  object Package extends GenPackageCompanion[Expr]

  // [PackageSignature] is a version of the AST that does not contain
  // LF expression. This should save memory in the [CompiledPackages]
  // where those expressions once compiled into Speedy Expression
  // become useless.
  // Here the term "Signature" refers to all the type information of
  // all the (serializable or not) data of a given package including
  // values and type synonyms. This contrasts with the term
  // "Interface" we conventional use to speak only about all the
  // types of the serializable data of a package. See for instance
  // [InterfaceReader]
  type PackageSignature = GenPackage[Unit]
  object PackageSignature extends GenPackageCompanion[Unit]

  val keyFieldName = Name.assertFromString("key")
  val valueFieldName = Name.assertFromString("value")
  val maintainersFieldName = Name.assertFromString("maintainers")
  val contractIdFieldName = Name.assertFromString("contractId")
  val contractFieldName = Name.assertFromString("contract")
  val signatoriesFieldName = Name.assertFromString("signatories")
  val observersFieldName = Name.assertFromString("observers")

  final case class PackageError(error: String) extends RuntimeException(error)

}
