// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.lf.codegen.backend.java.inner

import com.daml.ledger.javaapi
import com.daml.lf.typesig
import com.squareup.javapoet._
import com.typesafe.scalalogging.StrictLogging
import javax.lang.model.element.Modifier

import scala.jdk.CollectionConverters._

private[inner] object EnumClass extends StrictLogging {

  def generate(
      className: ClassName,
      enumeration: typesig.Enum,
  ): TypeSpec = {
    TrackLineage.of("enum", className.simpleName()) {
      logger.info("Start")
      val enumType = TypeSpec.enumBuilder(className).addModifiers(Modifier.PUBLIC)
      enumeration.constructors.foreach(c => enumType.addEnumConstant(c.toUpperCase()))
      enumType.addField(generateValuesArray(enumeration))
      enumType.addMethod(generateEnumsMapBuilder(className, enumeration))
      enumType.addField(generateEnumsMap(className))
      enumType.addMethod(generateFromValue(className, enumeration))
      enumType.addMethod(generateToValue(className))
      logger.debug("End")
      enumType.build()
    }
  }

  private def generateValuesArray(enumeration: typesig.Enum): FieldSpec = {
    val fieldSpec = FieldSpec.builder(ArrayTypeName.of(classOf[javaapi.data.DamlEnum]), "__values$")
    fieldSpec.addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
    val constructorValues = enumeration.constructors
      .map(c => CodeBlock.of("new $T($S)", classOf[javaapi.data.DamlEnum], c))
      .asJava
    fieldSpec.initializer(constructorValues.stream().collect(CodeBlock.joining(", ", "{", "}")))
    fieldSpec.build()
  }

  private def mapType(className: ClassName) =
    ParameterizedTypeName.get(
      ClassName.get(classOf[java.util.Map[Any, Any]]),
      ClassName.get(classOf[String]),
      className,
    )
  private def hashMapType(className: ClassName) =
    ParameterizedTypeName.get(
      ClassName.get(classOf[java.util.HashMap[Any, Any]]),
      ClassName.get(classOf[String]),
      className,
    )

  private def generateEnumsMap(className: ClassName): FieldSpec =
    FieldSpec
      .builder(mapType(className), "__enums$")
      .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
      .initializer("$T.__buildEnumsMap$$()", className)
      .build()

  private def generateEnumsMapBuilder(
      className: ClassName,
      enumeration: typesig.Enum,
  ): MethodSpec = {
    val builder = MethodSpec.methodBuilder("__buildEnumsMap$")
    builder.addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
    builder.returns(mapType(className))
    builder.addStatement("$T m = new $T()", mapType(className), hashMapType(className))
    enumeration.constructors.foreach(c =>
      builder.addStatement(s"""m.put("$c", ${c.toUpperCase()})""")
    )
    builder.addStatement("return m")
    builder.build()
  }

  private def generateFromValue(
      className: ClassName,
      enumeration: typesig.Enum,
  ): MethodSpec = {
    logger.debug(s"Generating fromValue static method for ${enumeration}")

    MethodSpec
      .methodBuilder("fromValue")
      .addModifiers(Modifier.STATIC, Modifier.PUBLIC, Modifier.FINAL)
      .returns(className)
      .addParameter(classOf[javaapi.data.Value], "value$")
      .addStatement(
        "$T constructor$$ = value$$.asEnum().orElseThrow(() -> new $T($S)).getConstructor()",
        classOf[String],
        classOf[IllegalArgumentException],
        s"Expected DamlEnum to build an instance of the Enum ${className.simpleName()}",
      )
      .addStatement(
        "if (!$T.__enums$$.containsKey(constructor$$)) throw new $T($S + constructor$$)",
        className,
        classOf[IllegalArgumentException],
        s"Expected a DamlEnum with ${className.simpleName()} constructor, found ",
      )
      .addStatement("return ($T) $T.__enums$$.get(constructor$$)", className, className)
      .build()

  }

  private def generateToValue(className: ClassName): MethodSpec =
    MethodSpec
      .methodBuilder("toValue")
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(classOf[javaapi.data.DamlEnum])
      .addStatement("return $T.__values$$[ordinal()]", className)
      .build()

}
