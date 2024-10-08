# YiLink
## Made for My Best Xyc～❤️
![image](https://raw.githubusercontent.com/lingyicute/YiLink/master/logo.png)

A V2Ray client for Android, support [Xray core](https://github.com/XTLS/Xray-core) and [v2fly core](https://github.com/v2fly/v2ray-core)

[![API](https://img.shields.io/badge/API-21%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/lollipop)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.23-blue.svg)](https://kotlinlang.org)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/lingyicute/YiLink)](https://github.com/lingyicute/YiLink/commits/master)
[![CodeFactor](https://www.codefactor.io/repository/github/lingyicute/yilink/badge)](https://www.codefactor.io/repository/github/lingyicute/yilink)
[![GitHub Releases](https://img.shields.io/github/downloads/lingyicute/YiLink/latest/total?logo=github)](https://github.com/lingyicute/YiLink/releases)

## Release

### [Latest release arm64](https://raw.githubusercontent.com/lingyicute/YiLink/master/apk-releases/latest.apk)
### [Latest release armeabi](https://raw.githubusercontent.com/lingyicute/YiLink/master/apk-releases/latest-32.apk)

## Usage

### Geoip and Geosite
- geoip.dat and geosite.dat files are in `Android/data/com.v2ray.yilink/files/assets` (path may differ on some Android device)
- download feature will get enhanced version in this [repo](https://github.com/Loyalsoldier/v2ray-rules-dat) (Note it need a working proxy)
- latest official [domain list](https://github.com/v2fly/domain-list-community) and [ip list](https://github.com/v2fly/geoip) can be imported manually
- possible to use third party dat file in the same folder, like [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

### VPN Mode
![image](https://raw.githubusercontent.com/lingyicute/YiLink/master/vpn-mode.jpg)

### Proxy only Mode
- ✔️ One less DNS, might be faster
- ✔️ Less processing means less battery usage, less memory usage
- ✔️ Other apps can take system VPN
- 🔴 Need manual configuration

### Development guide

Android project under YiLink folder can be compiled directly in Android Studio, or using Gradle wrapper. But the v2ray core inside the aar is (probably) outdated.  
The aar can be compiled from the Golang project [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite).
For a quick start, read guide for [Go Mobile](https://github.com/golang/go/wiki/Mobile) and [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/)

YiLink can run on Android Emulators. For WSA, VPN permission need to be granted via
`appops set [package name] ACTIVATE_VPN allow`
