# ee.j2se;version=11

# usage to

AdoptOpenJDK API - https://api.adoptopenjdk.net/

Note on the API rate limit of 100 API calls per hour per IP. Can be found in the API response header 'X-RateLimit-Remaining' requested via curl argument `-i`.
Mind to url encode the java version e.g. "jdk-11.0.3+7" -> "jdk-11.0.3%2B7" 

# Java 11

* command to list the all java 11 jdk releases
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk11'`

* command to list and grep attributes of java 11 releases of the hotspot implementation
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk11?openjdk_impl=hotspot' | grep release_name`
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk11?openjdk_impl=hotspot&release=jdk-11.0.3%2B7' | grep architecture`

* command to list the latest java 11 jdk for hotspot implementation of jdk for os=win32,arch=x86_64
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk11?openjdk_impl=hotspot&os=windows&arch=x64&release=latest&type=jdk'`

* command to list all details for a specific version e.g. "jdk-11.0.3+7" for win32|macosx|linux
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk11?openjdk_impl=hotspot&release=jdk-11.0.3%2B7&arch=x64&os=windows'`
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk11?openjdk_impl=hotspot&release=jdk-11.0.3%2B7&arch=x64&os=mac'`
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk11?openjdk_impl=hotspot&release=jdk-11.0.3%2B7&arch=x64&os=linux'`
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk11?openjdk_impl=hotspot&release=jdk-11.0.3%2B7&arch=arm&os=linux'`

* command to download the a specific java 11 jdk for hotspot implementation of jdk for os=win32,arch=x86_64
`curl -L -O 'https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.3%2B7/OpenJDK11U-jdk_x64_windows_hotspot_11.0.3_7.zip'`
`curl -L -O 'https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.3%2B7/OpenJDK11U-jdk_x64_linux_hotspot_11.0.3_7.tar.gz'`
`curl -L -O 'https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.3%2B7/OpenJDK11U-jdk_x64_mac_hotspot_11.0.3_7.tar.gz'`

* command to download the a specific java 11 jdk for hotspot implementation of jre for os=win32,arch=x86_64
`curl -L -O 'https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.3%2B7/OpenJDK11U-jre_x64_windows_hotspot_11.0.3_7.zip'`
`curl -L -O 'https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.3%2B7/OpenJDK11U-jre_x64_linux_hotspot_11.0.3_7.tar.gz'`
`curl -L -O 'https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.3%2B7/OpenJDK11U-jre_x64_mac_hotspot_11.0.3_7.tar.gz'`

# Java 8

* command to list all available java 8 releases
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk8'`

* command to list the latest java 8 jdk releases for os=win32,arch=x86_64
`curl -L 'https://api.adoptopenjdk.net/v2/info/releases/openjdk8?openjdk_impl=hotspot&os=windows&arch=x64&release=latest&type=jdk'`

