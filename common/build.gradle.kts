plugins {
    `java-library`
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    // Jackson v3 uses the package name "tools.jackson" instead of "com.fasterxml.jackson"
    implementation("tools.jackson.core:jackson-core:3.2.1")
    implementation("tools.jackson.core:jackson-databind:3.2.1")
    // There is no v3 of jackson-annotations, so we use the latest v2 version
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.20.0") 
}