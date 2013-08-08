package org.kevoree.kcl.loader

import java.io.InputStream
import java.io.ByteArrayInputStream
import java.util.concurrent.Callable
import org.kevoree.kcl.KevoreeLazyJarResources
import org.kevoree.kcl.KevoreeJarClassLoader
import org.kevoree.kcl.KCLScheduler
import org.kevoree.log.Log

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 19/03/12
 * Time: 14:53
 */

class KevoreeLocalLoader(val classpathResources: KevoreeLazyJarResources, val kcl: KevoreeJarClassLoader): ProxyClassLoader() {

    KevoreeLocalLoader(classpathResources: KevoreeLazyJarResources, kcl: KevoreeJarClassLoader){
        order = 1
    }


    public override fun loadResource(name: String?): InputStream? {
        if(name != null){
            val arr = classpathResources.getResource(name)
            if (arr != null) {
                return ByteArrayInputStream(arr)
            }
        }
        return null
    }

    public override fun loadClass(className: String?, resolveIt: Boolean): Class<out Any?>? {
        var result = kcl.getLoadedClass(className!!)
        if (result == null) {
            val bytes = kcl.loadClassBytes(className)
            if (bytes != null) {
                result = kcl.getLoadedClass(className)
                if (result == null) {
                    result = kcl.internal_defineClass(className, bytes)
                }
                releaseLock(className!!)
            }
        }
        return result
    }

    inner class AcquireLockCallable(val className: String): Callable<InternalLock> {
        override fun call(): InternalLock? {
            if (locked.containsKey(className)) {
                return locked.get(className)!!
            } else {
                locked.put(className, InternalLock(true))
                return null //don't block first thread
            }
        }
    }

    fun acquireLock(className: String) {
        val call = AcquireLockCallable(className)
        try {
            val obj = KCLScheduler.getScheduler().submit(call).get()
            if (obj != null){
                synchronized(obj, {
                    if(obj.isLocked){
                        (obj as java.lang.Object).wait()
                    }
                })
            }
        } catch(e: Throwable){

            if(Log.ERROR){
                Log.error("Error while sync " + className + " KCL thread : " + Thread.currentThread().getName(), e)
            }

        }
    }

    inner class ReleaseLockCallable(val className: String): Runnable {
        override fun run() {
            if (locked.containsKey(className)) {
                val lobj = locked.get(className)!!
                locked.remove(className)
                synchronized(lobj, {
                    lobj.isLocked = false
                    (lobj as java.lang.Object).notifyAll()
                })
            }
        }
    }

    fun releaseLock(className: String) {
        try {
            val call = ReleaseLockCallable(className)
            KCLScheduler.getScheduler().submit(call).get()
        } catch(ie: java.lang.InterruptedException) {
        }
        catch (e: Throwable) {
            if(Log.ERROR){
                Log.error("Error while sync " + className + " KCL thread : " + Thread.currentThread().getName(), e)
            }
        }
    }

    private val locked = java.util.HashMap<String, InternalLock>()

    class InternalLock(public var isLocked : Boolean)


}
