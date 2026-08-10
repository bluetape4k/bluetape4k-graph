dependencies {
    api(project(":bluetape4k-graph-io-core"))
    api(bt4k.bluetape4k.okio)
    api(bt4k.bluetape4k.tink)

    // 기존 graph-io 모듈 — 포맷별 임포터/익스포터 위임
    implementation(project(":bluetape4k-graph-io-csv"))
    implementation(project(":bluetape4k-graph-io-jackson2"))
    implementation(project(":bluetape4k-graph-io-jackson3"))
    implementation(project(":bluetape4k-graph-io-graphml"))

    implementation(bt4k.bluetape4k.coroutines)
    implementation(bt4k.bluetape4k.virtualthread.api)
    implementation(bt4k.bluetape4k.virtualthread.jdk25)

    // 압축 런타임 — 선택적 의존성 (없어도 GZip/Deflate/Bzip2는 JDK 내장으로 동작)
    compileOnly(bt4k.snappy.java)
    compileOnly(bt4k.at.yawk.lz4.java)
    compileOnly(bt4k.zstd.jni)
    compileOnly(bt4k.commons.compress)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(bt4k.okio.fakefilesystem)
    testImplementation(project(":bluetape4k-graph-tinkerpop"))

    // 통합 테스트에서 압축 알고리즘 실제 사용
    testImplementation(bt4k.snappy.java)
    testImplementation(bt4k.at.yawk.lz4.java)
    testImplementation(bt4k.zstd.jni)
    testImplementation(bt4k.commons.compress)
}
