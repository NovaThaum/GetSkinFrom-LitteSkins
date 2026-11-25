dependencies {
    implementation("org.yaml:snakeyaml:2.3")
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.0.0")
    testImplementation("net.skinsrestorer:skinsrestorer-api:15.0.0")
    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("org.slf4j:slf4j-api:2.0.17")
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
}

tasks.test {
    useJUnitPlatform()
}
