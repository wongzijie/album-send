# Third-party notices

The MIT license in this repository covers the application's own source code. It does not replace the licenses of its dependencies.

| Component | Version / scope | License and upstream |
| --- | --- | --- |
| jcifs-ng | 2.1.10, SMB client | LGPL 2.1; [source tag](https://github.com/AgNO3/jcifs-ng/tree/jcifs-ng-2.1.10), [license](licenses/jcifs-ng-LGPL-2.1.txt) |
| Glide | 4.16.0 and its transitive modules | BSD-style and bundled component notices; [upstream](https://github.com/bumptech/glide/tree/v4.16.0), [license text](licenses/glide.txt) |
| AndroidX | Runtime UI and lifecycle dependencies | Apache 2.0; [upstream](https://android.googlesource.com/platform/frameworks/support/), [license](licenses/Apache-2.0.txt) |
| Kotlin / kotlinx.coroutines | stdlib 1.9.24; coroutines 1.6.4, transitive | Apache 2.0; [Kotlin](https://github.com/JetBrains/kotlin), [coroutines](https://github.com/Kotlin/kotlinx.coroutines), [license](licenses/Apache-2.0.txt) |
| SLF4J | API and nop 1.7.36 | MIT; [upstream](https://github.com/qos-ch/slf4j), [license](licenses/slf4j-MIT.txt) |
| Bouncy Castle | bcprov-jdk18on 1.76 | MIT; [upstream](https://github.com/bcgit/bc-java/tree/r1rv76), [license](licenses/bouncycastle-license.html) |
| Gradle wrapper | 9.2.1, build tooling | Apache 2.0; [upstream](https://github.com/gradle/gradle), [license](licenses/Apache-2.0.txt) |

These are unmodified upstream dependencies resolved through Gradle. The runtime dependency inventory is in [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md). JUnit and Impacket are test tooling and are not bundled into the APK.

The APK includes these notices and license texts at `assets/third-party-notices.txt`. A copy of the unmodified jcifs-ng 2.1.10 source distribution is supplied as a Release asset. The complete application source and Gradle build configuration are available in this repository, so the application can be rebuilt with a modified library. There is no additional application restriction on modification or reverse engineering for debugging changes to the LGPL-covered library.

Android and AndroidX are trademarks of their respective owners. This project is independent and is not endorsed by any NAS vendor or by Google.
