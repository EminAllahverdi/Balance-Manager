# Glance finds which widgets to redraw by their GlanceAppWidget's class name. R8 merges widget classes of the same shape
# into one, and then redrawing one kind of widget draws every kind with its layout.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

# Kotlinx serialization (navigation routes)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; }
