# kotlinx.serialization ships its own consumer rules; the protocol models are @Serializable.
-keep,includedescriptorclasses class tv.hospitium.launcher.protocol.**$$serializer { *; }
-keepclassmembers class tv.hospitium.launcher.protocol.** {
    *** Companion;
}
