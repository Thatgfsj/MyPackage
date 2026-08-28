package com.thatgfsj.mypackage.data

enum class Platform(val label: String, val packages: List<String> = emptyList()) {
    PDD("拼多多", listOf("com.xunmeng.pinduoduo")),
    TAOBAO("淘宝", listOf("com.taobao.taobao")),
    CAINIAO("菜鸟", listOf("com.cainiao.wireless")),
    CUSTOM("自定义");

    companion object {
        fun from(name: String?): Platform =
            entries.firstOrNull { it.name == name } ?: CUSTOM
    }
}
