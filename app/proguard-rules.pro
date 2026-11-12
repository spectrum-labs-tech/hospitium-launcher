# kotlinx.serialization ships its own consumer rules; the protocol models are @Serializable.
-keep,includedescriptorclasses class tech.spectrumlabs.hospitium.launcher.protocol.**$$serializer { *; }
-keepclassmembers class tech.spectrumlabs.hospitium.launcher.protocol.** {
    *** Companion;
}
