# Legacy Technic Launcher

If you miss the original Technic launcher, this is for you!
It has been updated to support Microsoft 2-factor authentication and dependencies that have been broken over the past 10+ years have been fixed.

Tested Configuration (Working as of July 2025):
- Windows 11 24H2
- Java 8 x64 Update 111

What is NOT working:
- Macs with an Apple Silicon CPU (M1, M2, etc...) will not launch the game due to the required lwjgl library being x86-64. Maybe there is an ARM64 version of this, but I have not been able to find one

## Building
- Run:
    - Windows: `mvnw install`
    - MacOS/Linux: `./mvnw install`
- The outputted files will be in the `target` folder

## Community

Main Technic site is here: https://technicpack.net/

and the forums are here: https://forums.technicpack.net/
