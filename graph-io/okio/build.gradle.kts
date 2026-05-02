dependencies {
    api(project(":graph-io-core"))
    api(libs.bluetape4k.okio)

    // 기존 graph-io 모듈 — 포맷별 임포터/익스포터 위임
    implementation(project(":graph-io-csv"))
    implementation(project(":graph-io-jackson2"))
    implementation(project(":graph-io-jackson3"))
    implementation(project(":graph-io-graphml"))

    implementation(libs.bluetape4k.coroutines)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.virtualthread.jdk25)

    // 압축 런타임 — 선택적 의존성 (없어도 GZip/Deflate/Bzip2는 JDK 내장으로 동작)
    compileOnly(libs.snappy.java)
    compileOnly(libs.lz4.java)
    compileOnly(libs.zstd.jni)
    compileOnly(libs.commons.compress)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(libs.okio.fakefilesystem)
    testImplementation(project(":graph-tinkerpop"))

    // 통합 테스트에서 압축 알고리즘 실제 사용
    testImplementation(libs.snappy.java)
    testImplementation(libs.lz4.java)
    testImplementation(libs.zstd.jni)
    testImplementation(libs.commons.compress)
}
