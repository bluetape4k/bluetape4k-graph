package io.bluetape4k.graph.examples.code.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/** 모듈 정점 레이블. Gradle/Maven 모듈, 패키지, 서비스 단위를 표현한다. */
object ModuleLabel : VertexLabel("Module") {
    val name = string("name")
    val path = string("path")         // 파일시스템 경로
    val version = string("version")
    val language = string("language") // "kotlin", "java", etc.
}

/** 클래스/인터페이스 정점 레이블. Java/Kotlin 타입을 표현한다. */
object ClassLabel : VertexLabel("Class") {
    val name = string("name")
    val qualifiedName = string("qualifiedName")
    val module = string("module")
    val isAbstract = boolean("isAbstract")
    val isInterface = boolean("isInterface")
}

/** 함수/메서드 정점 레이블. 코드 내 callable 단위를 표현한다. */
object FunctionLabel : VertexLabel("Function") {
    val name = string("name")
    val signature = string("signature")
    val className = string("className")
    val module = string("module")
    val lineCount = integer("lineCount")
}

/** 모듈 간 의존 관계 간선 레이블. `A DEPENDS_ON B`는 A가 B를 의존함을 의미한다. */
object DependsOnLabel : EdgeLabel("DEPENDS_ON", ModuleLabel, ModuleLabel) {
    val dependencyType = string("dependencyType")  // "compile", "runtime", "test"
    val version = string("version")
}

/** 클래스 임포트 관계 간선 레이블. */
object ImportsLabel : EdgeLabel("IMPORTS", ClassLabel, ClassLabel)

/** 클래스 상속 관계 간선 레이블. `A EXTENDS B`는 A가 B를 상속함을 의미한다. */
object ExtendsLabel : EdgeLabel("EXTENDS", ClassLabel, ClassLabel)

/** 인터페이스 구현 관계 간선 레이블. */
object ImplementsLabel : EdgeLabel("IMPLEMENTS", ClassLabel, ClassLabel)

/** 함수 호출 관계 간선 레이블. */
object CallsLabel : EdgeLabel("CALLS", FunctionLabel, FunctionLabel) {
    val callCount = integer("callCount")   // 호출 횟수
    val isRecursive = boolean("isRecursive")
}

/** 클래스/함수가 특정 모듈에 속함을 나타내는 간선 레이블. */
object BelongsToLabel : EdgeLabel("BELONGS_TO", ClassLabel, ModuleLabel)  // Class/Function → Module
