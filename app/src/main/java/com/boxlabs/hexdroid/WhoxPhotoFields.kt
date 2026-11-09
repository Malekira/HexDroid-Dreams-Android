package com.boxlabs.hexdroid

/** WHOX emits fields in protocol order, regardless of their order in the request. */
internal data class WhoxPhotoFields(val fields: String, val token: String?) {
    fun values(parameters: List<String>): Map<Char,String>? {
        if(parameters.size!=fields.length || (token!=null && parameters.firstOrNull()!=token)) return null
        return fields.toList().zip(parameters).toMap()
    }
    companion object {
        fun fromLine(line:String):WhoxPhotoFields? {
            val selector=line.split(Regex("\\s+")).firstOrNull { it.startsWith('%') } ?: return null
            val requested=selector.substringAfter('%').substringBefore(',')
            val fields="tcuihsnfdlaor".filter { it in requested }
            if(fields.isEmpty()) return null
            val token=if('t' in fields) selector.substringAfter(',',"0").takeIf { it.matches(Regex("[0-9]{1,3}")) } ?: return null else null
            return WhoxPhotoFields(fields,token)
        }
    }
}
