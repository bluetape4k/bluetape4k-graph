dependencies {
    api(project(":graph-io-core"))
    api(Libs.bluetape4k_okio)

    // 기존 graph-io 모듈 — 포맷별 임포터/익스포터 위임
    implementation(project(":graph-io-csv"))
    implementation(project(":graph-io-jackson2"))
    implementation(project(":graph-io-jackson3"))
    implementation(project(":graph-io-graphml"))

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)

    // 압축 런타임 — 선택적 의존성 (없어도 GZip/Deflate/Bzip2는 JDK 내장으로 동작)
    compileOnly(Libs.snappy_java)
    compileOnly(Libs.lz4_java)
    compileOnly(Libs.zstd_jni)
    compileOnly(Libs.commons_compress)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.okio_fakefilesystem)
    testImplementation(project(":graph-tinkerpop"))

    // 통합 테스트에서 압축 알고리즘 실제 사용
    testImplementation(Libs.snappy_java)
    testImplementation(Libs.lz4_java)
    testImplementation(Libs.zstd_jni)
    testImplementation(Libs.commons_compress)
}
