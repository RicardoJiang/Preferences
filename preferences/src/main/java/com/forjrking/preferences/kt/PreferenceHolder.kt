package com.forjrking.preferences.kt

import android.app.Application
import android.content.SharedPreferences
import com.forjrking.preferences.crypt.AesCrypt
import com.forjrking.preferences.crypt.Crypt
import com.forjrking.preferences.kt.bindings.*
import com.forjrking.preferences.provide.createSharedPreferences
import com.forjrking.preferences.serialize.Serializer
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVLogLevel
import java.lang.reflect.Type
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

/***
 *SharedPreferences使用可以配合mmkv
 * @param name xml名称 this::class.java.simpleName 如果使用包名不同类字段相同会覆盖值
 * @param cryptKey 加密密钥  ｛原生sp多进程不支持加密  多进程本身数据不安全而且性能比较差综合考虑不加密｝
 * @param isMMKV  是否使用mmkv
 * @param isMultiProcess 是否使用多进程  建议mmkv搭配使用 sp性能很差
 */
open class PreferenceHolder(
    name: String? = null,
    cryptKey: String? = null,
    isMMKV: Boolean = false,
    isMultiProcess: Boolean = false
) {

    open val preferences: SharedPreferences by lazy {
        if (!isInitialized()) {
            throw IllegalStateException("PreferenceHolder is not initialed")
        }
        SpProxy(MMKV.mmkvWithID(name))
    }
    /** DES: 减小edit实例化时候集合多次创建开销 */
    internal val edit : SharedPreferences.Editor by lazy { preferences.edit() }
    
    /** DES: 加密实现 */
    private var crypt: Crypt? = null

    init {
        // 加密数据实例
        if (!isMMKV && !cryptKey.isNullOrEmpty()) {
            crypt = AesCrypt(cryptKey)
        }
    }

    /**
     * @param default 默认值
     * @param key 自定义key
     * @param caching 缓存开关
     * */
    protected inline fun <reified T : Any> bindToPreferenceField(
        default: T, key: String? = null, caching: Boolean = true
    ): ReadWriteProperty<PreferenceHolder, T> =
        bindToPreferenceField(T::class, object : TypeToken<T>() {}.type, default, key, caching)

    protected inline fun <reified T : Any> bindToPreferenceFieldNullable(
        key: String? = null, caching: Boolean = true
    ): ReadWriteProperty<PreferenceHolder, T?> =
        bindToPreferenceFieldNullable(T::class, object : TypeToken<T>() {}.type, key, caching)

    protected fun <T : Any> bindToPreferenceField(
        clazz: KClass<T>, type: Type,
        default: T, key: String?, caching: Boolean = true
    ): ReadWriteProperty<PreferenceHolder, T> =
        PreferenceFieldBinder(clazz, type, default, key, caching, crypt)

    protected fun <T : Any> bindToPreferenceFieldNullable(
        clazz: KClass<T>, type: Type,
        key: String?, caching: Boolean = true
    ): ReadWriteProperty<PreferenceHolder, T?> =
        PreferenceFieldBinderNullable(clazz, type, key, caching, crypt)

    /**
     *  Function used to clear all SharedPreference and PreferenceHolder data. Useful especially
     *  during tests or when implementing Logout functionality.
     */
    fun clear(safety: Boolean = true) {
        forEachDelegate { delegate, property ->
            if (safety && property.name.startsWith("_")) return@forEachDelegate
            delegate.clear(this, property)
        }
    }

    /** DES: 清理缓存字段 */
    fun clearCache() {
        forEachDelegate { delegate, _ ->
            delegate.clearCache()
        }
    }

    private fun forEachDelegate(f: (Clearable, KProperty<*>) -> Unit) {
        val properties = this::class.declaredMemberProperties
            .filterIsInstance<KProperty1<SharedPreferences, *>>()
        for (p in properties) {
            val prevAccessible = p.isAccessible
            if (!prevAccessible) p.isAccessible = true
            val delegate = p.getDelegate(preferences)
            if (delegate is Clearable) f(delegate, p)
            p.isAccessible = prevAccessible
        }
    }

    companion object {
        /** DES: 为了防止内存泄漏 */
        lateinit var context: Application
        /** DES: isInitialized 放到伴生对象外面会报错。。。 */
        fun isInitialized(): Boolean = ::context.isInitialized
        /** DES: 加解密其他实现 反射获取*/
//        var cryptClazz: KClass<Crypt>? = null
        /** DES: 序列化接口 */
        var serializer: Serializer? = null
            get() {
                return field ?: throw ExceptionInInitializerError("serializer is null")
            }

        fun migrate(migrateSp:SpProxy,preferences: SharedPreferences){
            val kvs = preferences.all
           if (kvs != null && kvs.isNotEmpty()) {
               val iterator = kvs.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val key = entry.key
                    val value = entry.value
                    if (key != null && value != null) {
                        migrateSp.run {
                            when (value) {
                                is Boolean -> this.putBoolean(key, value)
                                is Int ->  this.putInt(key,value)
                                is Long -> this.putLong(key,value)
                                is Float -> this.putFloat(key, value)
                                is String -> this.putString(key,value)
                                else -> {}
                            }
                        }
                    }
                }
                kvs.size
            }
        }
    }
}