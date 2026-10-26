# Third-party components

The Android app resolves its native player from Maven Central:

- Coordinate: `dev.jdtech.mpv:libmpv:1.0.0`
- Producer: <https://github.com/jarnedemeulemeester/libmpv-android>
- Wrapper license: MIT. The wrapper's license does not replace the separate licenses of bundled native dependencies.
- Artifact: <https://repo.maven.apache.org/maven2/dev/jdtech/mpv/libmpv/1.0.0/libmpv-1.0.0.aar>

Native upstreams and source/license references:

- mpv: <https://github.com/mpv-player/mpv> — see upstream `Copyright` and the exact build configuration.
- FFmpeg: <https://ffmpeg.org/legal.html> — licensing depends on enabled components.
- libplacebo: <https://github.com/haasn/libplacebo> — LGPL-2.1-or-later.
- libass: <https://github.com/libass/libass> — ISC; also includes separately licensed dependencies.
- Kotlin / AndroidX / OkHttp / Coil retain their upstream notices and licenses.

The Gradle launcher and wrapper JAR are provided by Gradle under Apache-2.0. Their upstream is <https://github.com/gradle/gradle>.

Before public distribution, identify all native build revisions and satisfy their notices, source/relinking obligations and any applicable commercial terms. No Dolby certification or authorization to use Dolby branding is asserted by this prototype.

Development-only test media comes from Dolby's public test kit and is not embedded in the app APK:

<https://ott.dolby.com/OnDelKits/DolbyVision_OSS_Media_Kit/clear/>

The screenshots of those test patterns are validation evidence, not app-provided video content.
