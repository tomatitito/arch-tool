package com.breuninger.arch.ir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TypeSpec extends AnyFlatSpec with Matchers {

  "PrimitiveType" should "not be nullable by default" in {
    Type.IntType.isNullable shouldBe false
    Type.StringType.isNullable shouldBe false
    Type.BooleanType.isNullable shouldBe false
  }

  it should "create nullable variant" in {
    val nullableInt = Type.IntType.makeNullable
    nullableInt.isNullable shouldBe true
    nullableInt shouldBe Type.NullableType(Type.IntType)
  }

  "NamedType" should "support simple types" in {
    val userType = Type.NamedType("com.example.User")
    userType.qualifiedName shouldBe "com.example.User"
    userType.typeArguments shouldBe Nil
  }

  it should "support generic types" in {
    val listOfUsers = Type.NamedType(
      "com.example.List",
      List(Type.NamedType("com.example.User"))
    )
    listOfUsers.typeArguments should have size 1
  }

  "CollectionType" should "represent lists" in {
    val intList = Type.ListType(Type.IntType)
    intList.elementType shouldBe Type.IntType
    intList.isNullable shouldBe false
  }

  it should "represent sets" in {
    val stringSet = Type.SetType(Type.StringType)
    stringSet.elementType shouldBe Type.StringType
  }

  it should "represent maps" in {
    val stringIntMap = Type.MapType(Type.StringType, Type.IntType)
    stringIntMap.keyType shouldBe Type.StringType
    stringIntMap.valueType shouldBe Type.IntType
  }

  "NullableType" should "wrap underlying type" in {
    val nullableString = Type.NullableType(Type.StringType)
    nullableString.isNullable shouldBe true
    nullableString.underlying shouldBe Type.StringType
  }

  it should "return itself when made nullable" in {
    val nullableString = Type.NullableType(Type.StringType)
    nullableString.makeNullable shouldBe nullableString
  }

  it should "unwrap when made non-nullable" in {
    val nullableString = Type.NullableType(Type.StringType)
    nullableString.makeNonNullable shouldBe Type.StringType
  }

  "FunctionType" should "represent function signatures" in {
    val stringToInt = Type.FunctionType(
      List(Type.StringType),
      Type.IntType
    )
    stringToInt.parameterTypes shouldBe List(Type.StringType)
    stringToInt.returnType shouldBe Type.IntType
  }

  it should "support multiple parameters" in {
    val addFunction = Type.FunctionType(
      List(Type.IntType, Type.IntType),
      Type.IntType
    )
    addFunction.parameterTypes should have size 2
  }

  "TypeParameter" should "represent generic type variables" in {
    val typeT = Type.TypeParameter("T")
    typeT.name shouldBe "T"
    typeT.bounds shouldBe Nil
  }

  it should "support bounded type parameters" in {
    val boundedT = Type.TypeParameter(
      "T",
      List(Type.NamedType("com.example.Base"))
    )
    boundedT.bounds should have size 1
  }
}
