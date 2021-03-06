package com.hyunh.certy.sgp23

import androidx.annotation.IntDef
import androidx.annotation.XmlRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hyunh.certy.Certy
import com.hyunh.certy.R
import com.hyunh.certy.logd
import com.hyunh.certy.logi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

object Rsp {

    private const val TAG = "Rsp"

    private val sources = arrayOf(
            R.xml.sgp23_v_1_3,
            R.xml.sgp23_v_1_6
    )

    init {
        sources.forEach {
            CoroutineScope(Dispatchers.Default).launch {
                Loader(it).load()
            }
        }
    }

    data class Condition(
            val id: String,
            val condition: String,
            val mnemonics: List<String>
    )

    data class Option(
            val type: String,
            val option: String,
            val mnemonic: String
    )

    data class TestCase(
            val id: String,
            val name: String,
            val roles: String,
            val testEnv: String?,
            val mocs: List<Pair<String, String>>
    )

    var version = ""
    var sgp22Version = "2.2"
    val hideMandatoryTestCase: LiveData<Boolean> = MutableLiveData(false)

    private val options = mutableMapOf<String, List<Option>>()
    private val testCases = mutableMapOf<String, List<TestCase>>()
    private val conditions = mutableMapOf<String, List<Condition>>()

    private val optionsLiveData = mutableMapOf<String, LiveData<List<Option>>>()
    private val testCasesLiveData = mutableMapOf<String, LiveData<List<TestCase>>>()
    private val conditionsLiveData = mutableMapOf<String, LiveData<List<Condition>>>()

    private val selectedItems = MutableLiveData<MutableSet<Int>>(mutableSetOf())

    @IntDef(value = [
        OPTION,
        CONDITION,
        TESTCASE
    ])
    annotation class RspType
    const val NOTSET = 0
    const val OPTION = 1
    const val CONDITION = 2
    const val TESTCASE = 3

    fun hideMandatoryTestCase(hide: Boolean) {
        (hideMandatoryTestCase as MutableLiveData).postValue(hide)
    }

    fun resetSelection() {
        selectedItems.value?.clear()
        selectedItems.postValue(selectedItems.value)
    }

    fun selectItem(item: Int) {
        val sets = selectedItems.value ?: return

        if (sets.contains(item)) {
            sets.remove(item)
        } else {
            sets.add(item)
        }
        selectedItems.postValue(selectedItems.value)
    }

    fun loadSelectedItems() = selectedItems

    fun loadResultOptions(@RspType type: Int = NOTSET): List<Option> {
        val options = options[version] ?: return emptyList()
        val filter = selectedItems.value ?: return options

        return when (type) {
            CONDITION -> {
                val source = conditions[version]?.filterIndexed { index, _ ->
                    filter.contains(index)
                } ?: return emptyList()

                mutableListOf<Option>().apply {
                    source.forEach condition@ { condition ->
                        options.forEach option@ { option ->
                            if (condition.mnemonics.contains(option.mnemonic)) {
                                add(option)
                            }
                        }
                    }
                }
            }
            TESTCASE -> {
                val source = testCases[version]?.filterIndexed { index, _ ->
                    filter.contains(index)
                } ?: return emptyList()
                val conditions = conditions[version] ?: emptyList()

                mutableListOf<Option>().apply {
                    source.forEach testCase@{ testcase ->
                        testcase.mocs.forEach condition@{ pair ->
                            if (pair.first != "M" && pair.second == sgp22Version) {
                                val mnemonics = conditions.find { it.id == pair.first }?.mnemonics
                                        ?: emptyList()
                                options.forEach option@{ option ->
                                    if (mnemonics.contains(option.mnemonic)) {
                                        add(option)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> throw IllegalArgumentException("Invalid type: $type")
        }
    }

    fun loadResultConditions(@RspType type: Int): List<Condition> {
        val conditions = conditions[version] ?: return emptyList()
        val filter = selectedItems.value ?: return conditions

        return when (type) {
            OPTION -> {
                val source = options[version]?.filterIndexed { index, _ ->
                    filter.contains(index)
                } ?: return emptyList()

                mutableListOf<Condition>().apply {
                    source.forEach option@{ option ->
                        conditions.forEach condition@{ condition ->
                            if (condition.mnemonics.contains(option.mnemonic)) {
                                add(condition)
                            }
                        }
                    }
                }
            }
            TESTCASE -> {
                val source = testCases[version]?.filterIndexed { index, _ ->
                    filter.contains(index)
                } ?: return emptyList()

                mutableListOf<Condition>().apply {
                    source.forEach testCase@{ testCase ->
                        testCase.mocs.forEach condition@{ pair ->
                            if (pair.first != "M" && pair.second == sgp22Version) {
                                conditions.find { it.id == pair.first }?.let {
                                    add(it)
                                }
                            }
                        }
                    }
                }
            }
            else -> throw IllegalArgumentException("Invalid type: $type")
        }
    }

    fun loadResultTestcases(@RspType type: Int): List<TestCase> {
        val testCases = testCases[version] ?: return emptyList()
        val filter = selectedItems.value ?: return testCases

        return when (type) {
            OPTION -> {
                val source = options[version]?.filterIndexed { index, _ ->
                    filter.contains(index)
                } ?: return emptyList()

                mutableListOf<TestCase>().apply {
                    source.forEach option@{ option ->
                        testCases.forEach testCase@{ testCase ->
                            testCase.mocs.forEach condition@{ pair ->
                                if (pair.second == sgp22Version) {
                                    if (pair.first == option.mnemonic || pair.first == "M") {
                                        add(testCase)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            CONDITION -> {
                val source = conditions[version]?.filterIndexed { index, _ ->
                    filter.contains(index)
                } ?: return emptyList()

                mutableListOf<TestCase>().apply {
                    source.forEach condition@{ condition ->
                        testCases.forEach testCase@{ testCase ->
                            testCase.mocs.forEach condition@{ pair ->
                                if (pair.second == sgp22Version) {
                                    if (pair.first == condition.id || pair.first == "M") {
                                        add(testCase)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> throw IllegalArgumentException("Invalid type: $type")
        }
    }

    fun loadOptions(): LiveData<List<Option>> {
        return optionsLiveData[version] ?: run {
            MutableLiveData<List<Option>>().also {
                optionsLiveData[version] = it
            }
        }
    }

    fun loadTestCases(): LiveData<List<TestCase>> {
        return testCasesLiveData[version] ?: run {
            MutableLiveData<List<TestCase>>().also {
                testCasesLiveData[version] = it
            }
        }
    }

    fun loadConditions(): LiveData<List<Condition>> {
        return conditionsLiveData[version] ?: run {
            MutableLiveData<List<Condition>>().also {
                conditionsLiveData[version] = it
            }
        }
    }

    private class Loader(@XmlRes resId: Int) {

        companion object {
            private const val ROOT = "specification"
            private const val SGP23 = "SGP.23"
            private const val VERSION = "version"
            private const val ITEM = "item"
            private const val OPTION = "table_4"
            private const val OPTION_TYPE = "type"
            private const val OPTION_OPTION = "option"
            private const val OPTION_MNEMONIC = "mnemonic"

            private const val TESTCASE = "table_5"
            private const val CONDITION = "table_6"
        }

        private val parser = Certy.getXmlParser(resId)

        suspend fun load() {
            logi(TAG, "load()")
            var event = withContext(Dispatchers.IO) {
                parser.next()
            }
            while (event != XmlPullParser.START_TAG) {
                event = withContext(Dispatchers.IO) {
                    parser.next()
                }
            }
            if (parser.name != ROOT) {
                throw IllegalStateException("Root tag does not match - ${parser.name}")
            }

            var version: String? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        SGP23 -> {
                            if (version != null) {
                                throw IllegalStateException("Multiple SGP.23 tag is found")
                            }
                            version = parser.getAttributeValue(null, VERSION)
                        }
                        OPTION -> {
                            version?.let {
                                loadOption(parser, it)
                            } ?: throw IllegalStateException()
                        }
                        TESTCASE -> {
                            version?.let {
                                loadTestCase(parser, it)
                            } ?: throw IllegalStateException()

                        }
                        CONDITION -> {
                            version?.let {
                                loadCondition(parser, it)
                            } ?: throw IllegalStateException()
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if (parser.name == SGP23) {
                        version ?: throw IllegalStateException()
                        version = null
                    }
                }
                event = withContext(Dispatchers.IO) {
                    parser.next()
                }
            }
            logi(TAG, "load() completed")
        }

        private fun loadOption(parser: XmlPullParser, version: String) {
            val opt = mutableListOf<Option>()
            while (parser.next() != XmlPullParser.END_TAG || parser.name != OPTION) {
                if (parser.name == ITEM && parser.eventType == XmlPullParser.START_TAG) {
                    val type = parser.getAttributeValue(null, OPTION_TYPE)
                    val option = parser.getAttributeValue(null, OPTION_OPTION)
                    val mnemonic = parser.getAttributeValue(null, OPTION_MNEMONIC)
                    opt.add(Option(type, option, mnemonic).also {
                        logd(TAG, "loadOption() $it")
                    })
                }
            }
            options[version] = opt
            (optionsLiveData[version] as? MutableLiveData)?.postValue(opt)
                    ?: MutableLiveData<List<Option>>(opt).also {
                        optionsLiveData[version] = it
                    }
        }

        private fun loadTestCase(parser: XmlPullParser, version: String) {
            val testCase = mutableListOf<TestCase>()
            while (parser.next() != XmlPullParser.END_TAG || parser.name != TESTCASE) {
                if (parser.name == ITEM && parser.eventType == XmlPullParser.START_TAG) {
                    val id = parser.getAttributeValue(null, "id")
                    val name = parser.getAttributeValue(null, "name")
                    val roles = parser.getAttributeValue(null, "roles")
                    val testEnv = parser.getAttributeValue(null, "testenv")
                    val mocs = mutableListOf<Pair<String, String>>()
                    while (parser.next() != XmlPullParser.END_TAG || parser.name != ITEM) {
                        if (parser.name == "condition" && parser.eventType == XmlPullParser.START_TAG) {
                            val pair = Pair(parser.getAttributeValue(null, "condition"),
                                    parser.getAttributeValue(null, "version"))
                            mocs.add(pair)
                        }
                    }
                    testCase.add(TestCase(id, name, roles, testEnv, mocs).also {
                        logd(TAG, "loadTestCase() $it")
                    })
                }
            }
            testCases[version] = testCase
            (testCasesLiveData[version] as? MutableLiveData)?.postValue(testCase)
                    ?: MutableLiveData<List<TestCase>>(testCase).also {
                        testCasesLiveData[version] = it
                    }
        }

        private fun loadCondition(parser: XmlPullParser, version: String) {
            val cond = mutableListOf<Condition>()
            while (parser.next() != XmlPullParser.END_TAG || parser.name != CONDITION) {
                if (parser.name == ITEM && parser.eventType == XmlPullParser.START_TAG) {
                    val id = parser.getAttributeValue(null, "id")
                    val condition = parser.getAttributeValue(null, "condition")
                    val mnemonics = mutableListOf<String>()
                    while (parser.next() != XmlPullParser.END_TAG || parser.name != ITEM) {
                        if (parser.name == OPTION_MNEMONIC && parser.eventType == XmlPullParser.START_TAG) {
                            val mnemonic = parser.getAttributeValue(null, "id")
                            mnemonics.add(mnemonic)
                        }
                    }
                    cond.add(Condition(id, condition, mnemonics).also {
                        logd(TAG, "loadCondition() $it")
                    })
                }
            }
            conditions[version] = cond
            (conditionsLiveData[version] as? MutableLiveData)?.postValue(cond)
                    ?: MutableLiveData<List<Condition>>(cond).also {
                        conditionsLiveData[version] = it
                    }
        }
    }
}
