package org.kevoree.tools.aether.framework


import java.io.File
import org.slf4j.LoggerFactory
import org.kevoree.DeployUnit
import org.kevoree.kcl.KevoreeJarClassLoader
import java.util.ArrayList
import org.kevoree.api.service.core.classloading.KevoreeClassLoaderHandler
import org.kevoree.api.service.core.classloading.DeployUnitResolver
import java.util.concurrent.Callable
import java.util.concurrent.ThreadFactory
import java.util.HashMap
import java.util.concurrent.Executors

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 26/01/12
 * Time: 14:29
 */

open class JCLContextHandler: KevoreeClassLoaderHandler {

    val kcl_cache = java.util.HashMap<String, KevoreeJarClassLoader>()
    val kcl_cache_file = java.util.HashMap<String, File>()
    var lockedDu = ArrayList<String>()
    val logger = LoggerFactory.getLogger(this.javaClass)!!
    val resolvers = ArrayList<DeployUnitResolver>()
    protected val failedLinks: HashMap<String, KevoreeJarClassLoader> = HashMap<String, KevoreeJarClassLoader>()

    inner class DUMP(): Runnable {
        override fun run() {
            printDumpInternals()
        }
    }

    inner class INSTALL_DEPLOYUNIT_FILE(val du: DeployUnit, val file: File): Callable<KevoreeJarClassLoader> {
        override fun call(): KevoreeJarClassLoader {
            return installDeployUnitInternals(du, file)
        }
    }

    inner class INSTALL_DEPLOYUNIT(val du: DeployUnit): Callable<KevoreeJarClassLoader> {
        override fun call(): KevoreeJarClassLoader {
            return installDeployUnitNoFileInternals(du)!!
        }
    }

    inner class REMOVE_DEPLOYUNIT(val du: DeployUnit): Runnable {
        override fun run() {
            removeDeployUnitInternals(du)
        }
    }

    inner class GET_KCL(val du: DeployUnit): Callable<KevoreeJarClassLoader?> {
        override fun call(): KevoreeJarClassLoader? {
            return getKCLInternals(du)
        }
    }

    inner class MANUALLY_ADD_TO_CACHE(val du: DeployUnit, val kcl: KevoreeJarClassLoader, val toLock: Boolean): Runnable {
        override fun run() {
            manuallyAddToCacheInternals(du, kcl, toLock)
        }
    }

    inner class CLEAR(): Runnable {
        override fun run() {
            clearInternals()
        }
    }

    override fun registerDeployUnitResolver(val dur: DeployUnitResolver?) {
        pool.submit(Add_Resolver(dur!!))
    }
    inner class Add_Resolver(val dur: DeployUnitResolver): Runnable {
        override fun run() {
            resolvers.add(dur)
        }
    }
    override fun unregisterDeployUnitResolver(dur: DeployUnitResolver?) {
        pool.submit(Remove_Resolver(dur!!))
    }
    inner class Remove_Resolver(val dur: DeployUnitResolver): Runnable {
        override fun run() {
            resolvers.remove(dur)
        }
    }

    //class KILLActor()

    fun stop() {
        //TODO REMOVE ALL BEFORE
        pool.shutdownNow()
        resolvers.clear()
    }

    val pool = Executors.newSingleThreadExecutor(KCLHandlerThreadFactory())

    inner class GET_CACHE_FILE(val du: DeployUnit): Callable<File> {
        override fun call(): File {
            return getCacheFileInternals(du)
        }
    }

    fun clear() {
        pool.submit(CLEAR()).get()
    }

    override fun getCacheFile(du: DeployUnit?): File {
        return pool.submit(GET_CACHE_FILE(du!!)).get()!!
    }

    fun manuallyAddToCache(du: DeployUnit, kcl: KevoreeJarClassLoader) {
        pool.submit(MANUALLY_ADD_TO_CACHE(du, kcl, true))
    }

    override fun attachKCL(du: DeployUnit?, kcl: KevoreeJarClassLoader?) {
        pool.submit(MANUALLY_ADD_TO_CACHE(du!!, kcl!!, false)).get()
    }

    fun printDump() {
        pool.submit(DUMP())
    }

    protected fun clearInternals() {
        logger.debug("Clear Internal")
        for(key in ArrayList(kcl_cache.keySet())) {
            if (!lockedDu.contains(key)) {
                if (kcl_cache.containsKey(key)) {
                    logger.debug("Remove KCL for {}", key)
                    kcl_cache.get(key)!!.unload()
                    kcl_cache.remove(key)
                }
                if (kcl_cache_file.containsKey(key)) {
                    kcl_cache_file.remove(key)
                }
            }
        }
        failedLinks.clear()
    }

    protected fun getCacheFileInternals(du: DeployUnit?): File {
        return kcl_cache_file.get(buildKEY(du!!))!!
    }

    private fun manuallyAddToCacheInternals(du: DeployUnit, kcl: KevoreeJarClassLoader, toLock: Boolean) {
        kcl_cache.put(buildKEY(du), kcl)
        if(toLock){
            lockedDu.add(buildKEY(du))
        }
    }

    protected fun printDumpInternals() {
        logger.debug("------------------ KCL Dump -----------------------")
        for(k in kcl_cache) {
            logger.debug("Dump = {}", k.component1())
            k.component2().printDump()
        }
        logger.debug("================== End KCL Dump ===================")
    }


    /* Temp Zone for temporary unresolved KCL links */

    fun clearFailedLinks() {
        failedLinks.clear()
    }

    open fun installDeployUnitInternals(du: DeployUnit, file: File): KevoreeJarClassLoader {
        val previousKCL = getKCLInternals(du)
        val res = if (previousKCL != null) {
            logger.debug("Take already installed {}", buildKEY(du))
            previousKCL
        } else {
            logger.debug("Install {} , file {}", buildKEY(du), file)
            val newcl = KevoreeJarClassLoader()
            if (System.getProperty("kcl.lazy") != null && "true".equals(System.getProperty("kcl.lazy"))) {
                newcl.setLazyLoad(true)
            } else {
                newcl.setLazyLoad(false)
            }
            newcl.add(file.getAbsolutePath())
            kcl_cache.put(buildKEY(du), newcl)
            kcl_cache_file.put(buildKEY(du), file)
            logger.debug("Add KCL for {}->{}", du.getUnitName(), buildKEY(du))

            //TRY TO RECOVER FAILED LINK
            if (failedLinks.containsKey(buildKEY(du))) {
                failedLinks.get(buildKEY(du))!!.addSubClassLoader(newcl)
                newcl.addWeakClassLoader(failedLinks.get(buildKEY(du))!!)
                failedLinks.remove(buildKEY(du))
                logger.debug("Failed Link {} remain size : {}", du.getUnitName(), failedLinks.size())
            }
            for(rLib in  du.getRequiredLibs()) {
                val kcl = getKCLInternals(rLib)
                if (kcl != null) {
                    logger.debug("Link KCL for {}->{}", du.getUnitName(), rLib.getUnitName())
                    newcl.addSubClassLoader(kcl)
                    kcl.addWeakClassLoader(newcl)
                    du.getRequiredLibs().filter{ rLibIn -> rLib != rLibIn }.forEach{ rLibIn ->
                        val kcl2 = getKCLInternals(rLibIn)
                        if (kcl2 != null) {
                            kcl.addWeakClassLoader(kcl2)
                            // logger.debug("Link Weak for {}->{}", rLib.getUnitName(), rLibIn.getUnitName())
                        }
                    }
                } else {
                    logger.debug("Fail link ! Warning ")
                    failedLinks.put(buildKEY(du), newcl)
                }
            }
            newcl
        }
        return res!!
    }

    protected fun getKCLInternals(du: DeployUnit): KevoreeJarClassLoader? {
        return kcl_cache.get(buildKEY(du))
    }

    protected fun removeDeployUnitInternals(du: DeployUnit) {
        val key = buildKEY(du)
        if (failedLinks.containsKey(key)) {
            failedLinks.remove(key)
        }
        if (!lockedDu.contains(key)) {
            val kcl_to_remove = kcl_cache.get(key)
            val tempKeys = ArrayList<String>()
            for(fl in failedLinks){
                if(fl.component2() == kcl_to_remove){
                    tempKeys.add(fl.component1())
                }
            }
            for(key in tempKeys){
                failedLinks.remove(key)
            }
            tempKeys.clear()

            if (!lockedDu.contains(key)) {
                if (kcl_cache.containsKey(key)) {
                    logger.debug("Try to remove KCL for {}->{}", du.getUnitName(), buildKEY(du))
                    logger.debug("Cache To cleanuip size" + kcl_cache.values().size() + "-" + kcl_cache.size() + "-" + kcl_cache.keySet().size())
                    for(vals in kcl_cache.values()) {
                        if (vals.getSubClassLoaders().contains(kcl_to_remove)) {
                            failedLinks.put(key, vals)
                            logger.debug("Pending Fail link " + key)
                        }
                        vals.cleanupLinks(kcl_to_remove!!)
                        logger.debug("Cleanup {} from {}", vals.toString(), du.getUnitName())
                    }
                }
                val toRemoveKCL = kcl_cache.get(key)
                toRemoveKCL!!.unload()
                kcl_cache.remove(key)
            }
            if (kcl_cache_file.containsKey(key)) {
                logger.debug("Cleanup Cache File" + kcl_cache_file.get(key)!!.getAbsolutePath())
                kcl_cache_file.get(key)!!.delete()
                kcl_cache_file.remove(key)
                logger.debug("Remove File Cache " + key)
            }
        }
    }


    fun buildKEY(du: DeployUnit): String {
        return du.getName() + "/" + buildQuery(du, null)
    }

    fun buildQuery(du: DeployUnit, repoUrl: String?): String {
        val query = StringBuilder()
        query.append("mvn:")
        if(repoUrl != null){
            query.append(repoUrl); query.append("!")
        }

        query.append(du.getGroupName())
        query.append("/")
        query.append(du.getUnitName())
        if(!du.getVersion().equals("default") && !du.getVersion().equals("")){
            query.append("/"); query.append(du.getVersion())
        }
        return query.toString()
    }

    override fun installDeployUnit(du: DeployUnit?, file: File?): KevoreeJarClassLoader {
        return pool.submit(INSTALL_DEPLOYUNIT_FILE(du!!, file!!)).get()!!
    }

    override fun getKevoreeClassLoader(du: DeployUnit?): KevoreeJarClassLoader? {
        return pool.submit(GET_KCL(du!!)).get()
    }

    override fun removeDeployUnitClassLoader(du: DeployUnit?) {
        pool.submit(REMOVE_DEPLOYUNIT(du!!)).get()
    }


    open fun installDeployUnitNoFileInternals(du: DeployUnit): KevoreeJarClassLoader? {
        var resolvedFile: File? = null
        resolvers.any{
            res ->
            try {
                resolvedFile = res.resolve(du)
                true
            } catch(e:Exception) {
                false
            }
        }
        if (resolvedFile == null) {
            resolvedFile = AetherUtil.resolveDeployUnit(du)
        }
        if (resolvedFile != null) {
            return installDeployUnitInternals(du, resolvedFile!!)
        } else {
            logger.error("Error while resolving deploy unit " + du.getUnitName())
            return null
        }
    }

    override fun installDeployUnit(du: DeployUnit?): KevoreeJarClassLoader {
        return pool.submit(INSTALL_DEPLOYUNIT(du!!)).get()!!
    }

    override fun getKCLDump(): String {
        val buffer = StringBuffer()
        for(k in kcl_cache) {
            buffer.append("KCL KEY name=" + k.component1() + "\n")
            buffer.append(k.component2().getKCLDump() + "\n")
        }
        return buffer.toString()
    }

}