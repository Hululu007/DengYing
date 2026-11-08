# 规则说明
#
# 本项目 release 未开混淆（见 app/build.gradle.kts 的注释），故此处只留
# 「一旦开启混淆必须保留」的最小集合，避免将来开混淆时踩坑。
#
# 为什么这几类必须保留：
#  - 数据模型：本地存储用 JSON 序列化，字段名即键名，改名会读不出旧数据。
#  - Room/协程等反射入口：AGP 默认规则已覆盖大部分，此处只补本项目自己的。

# 数据模型：JSON 字段名与类名都参与序列化/反序列化
-keep class com.dengying.player.domain.model.** { *; }
-keep class com.dengying.player.data.source.** { *; }

# 枚举的 values()/valueOf() 在部分混淆配置下会被裁掉
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
