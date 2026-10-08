package com.gpp.hooks

import com.gpp.GrindrPlus
import com.gpp.core.loge
import com.gpp.core.logd
import com.gpp.core.logi
import com.gpp.core.logs
import com.gpp.core.mapping.MappingDictionary
import com.gpp.persistence.model.SavedPhraseEntity
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.RetrofitUtils
import com.gpp.utils.RetrofitUtils.RETROFIT_NAME
import com.gpp.utils.RetrofitUtils.isDELETE
import com.gpp.utils.RetrofitUtils.isGET
import com.gpp.utils.RetrofitUtils.isPOST
import com.gpp.utils.hook
import com.gpp.utils.compat.XposedHelpers.getObjectField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.reflect.Constructor
import java.lang.reflect.Proxy

class LocalSavedPhrases : Hook(
    "Local saved phrases",
    "Save unlimited phrases locally"
) {
    private val phrasesRestService: String
        get() = MappingDictionary.resolve(
            "LocalSavedPhrases.PhrasesRestService",
            "com.grindrapp.android.api.PhrasesRestService"
        ) // 'v3/me/prefs'
    private val createSuccessResult = RetrofitUtils.SUCCESS_CLASS_NAME // r84 'Success(successValue='
    private val chatRestService = "com.grindrapp.android.chat.data.datasource.api.service.ChatRestService"
    // 26.16.1+ moved under chat.data.datasource.api.model (legacy chat.api.model path is gone).
    private val addSavedPhraseResponse: String
        get() = MappingDictionary.resolve(
            "LocalSavedPhrases.AddSavedPhraseResponse",
            "com.grindrapp.android.chat.data.datasource.api.model.AddSavedPhraseResponse"
        )
    private val phrasesResponse = "com.grindrapp.android.model.PhrasesResponse"
    private val phraseModel = "com.grindrapp.android.persistence.model.Phrase"

    override fun init() {
        logi("Initializing Local Saved Phrases Hook...")

        if (addSavedPhraseResponse.isEmpty()) {
            logi("Local saved phrases: AddSavedPhraseResponse remap skipped (absent on this DEX)")
            return
        }

        if (phrasesRestService.isEmpty()) {
            logi("Local saved phrases: PhrasesRestService remap skipped (absent on this DEX)")
            return
        }

        val chatRestServiceClass = findClass(chatRestService)
        val createSuccess = findClass(createSuccessResult).constructors.firstOrNull() ?: run {
            loge("Failed to find Success result constructor (check obfuscation)")
            return
        }
        val phrasesRestServiceClass = findClass(phrasesRestService)

        findClass(RETROFIT_NAME).hook("create", HookStage.AFTER) { param ->
            val service = param.getResult()
            if (service != null) {
                param.setResult(when {
                    chatRestServiceClass.isAssignableFrom(service.javaClass) -> {
                        logd("Proxying ChatRestService for local storage")
                        createChatRestServiceProxy(service, createSuccess)
                    }

                    phrasesRestServiceClass.isAssignableFrom(service.javaClass) -> {
                        logd("Proxying PhrasesRestService for local retrieval")
                        createPhrasesRestServiceProxy(service, createSuccess)
                    }

                    else -> service
                })
            }
        }
    }

    private fun createChatRestServiceProxy(
        originalService: Any,
        createSuccess: Constructor<*>
    ): Any {
        val savedPhraseConstructor = findClass(addSavedPhraseResponse).constructors.first()
        val invocationHandler = Proxy.getInvocationHandler(originalService)

        return Proxy.newProxyInstance(
            originalService.javaClass.classLoader,
            arrayOf(findClass(chatRestService))
        ) { proxy, method, args ->
            when {
                method.isPOST("v3/me/prefs/phrases") -> {
                    val phrase = getObjectField(args[0], "phrase") as String
                    logi("Adding new local phrase")

                    runBlocking {
                        val index = getCurrentPhraseIndex() + 1
                        addPhrase(index, phrase, 0, System.currentTimeMillis())
                        logs("Phrase saved locally")

                        val response = savedPhraseConstructor?.newInstance(index.toString())
                        createSuccess.newInstance(response)
                    }
                }

                method.isDELETE("v3/me/prefs/phrases/{id}") -> {
                    val id = args[0] as? String ?: "unknown"
                    logi("Deleting local phrase")

                    runBlocking {
                        val index = id.toLongOrNull() ?: getCurrentPhraseIndex()
                        deletePhrase(index)
                        logs("Deleted local phrase")
                        createSuccess.newInstance(Unit)
                    }
                }

                method.isPOST("v4/phrases/frequency/{id}") -> {
                    val id = args[0] as? String ?: "0"
                    logd("Incrementing phrase usage frequency")

                    runBlocking {
                        val index = id.toLongOrNull() ?: 0L
                        val phrase = getPhrase(index)
                        if (phrase != null) {
                            updatePhrase(
                                index,
                                phrase.text,
                                phrase.frequency + 1,
                                System.currentTimeMillis()
                            )
                        }
                        createSuccess.newInstance(Unit)
                    }
                }

                else -> invocationHandler.invoke(proxy, method, args)
            }
        }
    }

    private fun createPhrasesRestServiceProxy(
        originalService: Any,
        createSuccess: Constructor<*>
    ): Any {
        val phraseModelConstructor = GrindrPlus.loadClass(phraseModel).constructors.first()
        val phraseResponseConstructor = findClass(phrasesResponse)
            .constructors.find { it.parameterTypes.size == 1 }

        val invocationHandler = Proxy.getInvocationHandler(originalService)
        return Proxy.newProxyInstance(
            originalService.javaClass.classLoader,
            arrayOf(findClass(phrasesRestService))
        ) { proxy, method, args ->
            when {
                method.isGET("v3/me/prefs") -> {
                    logd("Intercepted GET v3/me/prefs - pulling from local database")
                    runBlocking {
                        val currentPhrases = getPhraseList()
                        logd("Loaded ${currentPhrases.size} local phrases")

                        val phrases = currentPhrases.associateWith { phrase ->
                            phraseModelConstructor?.newInstance(
                                phrase.phraseId.toString(), phrase.text, phrase.timestamp, phrase.frequency
                            )
                        }
                        val phrasesResponse = phraseResponseConstructor?.newInstance(phrases)
                        createSuccess.newInstance(phrasesResponse)
                    }
                }

                else -> invocationHandler.invoke(proxy, method, args)
            }
        }
    }

    private suspend fun getPhraseList(): List<SavedPhraseEntity> = withContext(Dispatchers.IO) {
        return@withContext GrindrPlus.database.savedPhraseDao().getPhraseList()
    }

    private suspend fun getPhrase(phraseId: Long): SavedPhraseEntity? = withContext(Dispatchers.IO) {
        return@withContext GrindrPlus.database.savedPhraseDao().getPhrase(phraseId)
    }

    private suspend fun getCurrentPhraseIndex(): Long = withContext(Dispatchers.IO) {
        return@withContext GrindrPlus.database.savedPhraseDao().getCurrentPhraseIndex() ?: 0L
    }

    private suspend fun addPhrase(phraseId: Long, text: String, frequency: Int, timestamp: Long) = withContext(Dispatchers.IO) {
        val phrase = SavedPhraseEntity(
            phraseId = phraseId,
            text = text,
            frequency = frequency,
            timestamp = timestamp
        )
        GrindrPlus.database.savedPhraseDao().upsertPhrase(phrase)
    }

    private suspend fun updatePhrase(phraseId: Long, text: String, frequency: Int, timestamp: Long) = withContext(Dispatchers.IO) {
        val phrase = SavedPhraseEntity(
            phraseId = phraseId,
            text = text,
            frequency = frequency,
            timestamp = timestamp
        )
        GrindrPlus.database.savedPhraseDao().upsertPhrase(phrase)
    }

    private suspend fun deletePhrase(phraseId: Long) = withContext(Dispatchers.IO) {
        GrindrPlus.database.savedPhraseDao().deletePhrase(phraseId)
    }
}