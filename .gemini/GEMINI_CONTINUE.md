# Gemini Continue

Module 1.5 is DONE. The build toolchain has been fixed by upgrading to Gradle 9.2.1 and Loom 1.15.4. `modImplementation` is correctly applied to `fabric-api` and `geckolib`, avoiding mapping issues.

Next Module to address: Module 2 (Client/Server Handshake).
We can now use `PayloadTypeRegistry` and standard Fabric networking since the compiler `class_8710` issue is resolved.