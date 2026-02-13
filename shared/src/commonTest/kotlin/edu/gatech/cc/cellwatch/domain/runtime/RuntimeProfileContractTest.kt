package edu.gatech.cc.cellwatch.domain.runtime

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RuntimeProfileContractTest {

    @Test
    fun localMsakMode_requiresLocalHost() {
        val config = RuntimeProfileConfig(
            msakMode = RuntimeMsakMode.LOCAL,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            strictSupabaseConfig = false,
        )

        assertFailsWith<IllegalStateException> {
            RuntimeProfileContract.requireValid(config)
        }
    }

    @Test
    fun testingSupabase_requiresRemoteEnablement() {
        val config = RuntimeProfileConfig(
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.TESTING,
            allowRemoteSupabase = false,
            testingSupabaseUrl = "https://testing.example.supabase.co",
            testingSupabaseApiKey = "testing-key",
        )

        assertFailsWith<IllegalStateException> {
            RuntimeProfileContract.requireValid(config)
        }
    }

    @Test
    fun localStrict_requiresLocalUrlAndKey() {
        val config = RuntimeProfileConfig(
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            strictSupabaseConfig = true,
            localSupabaseUrl = null,
            localSupabaseApiKey = null,
        )

        assertFailsWith<IllegalStateException> {
            RuntimeProfileContract.requireValid(config)
        }
    }
}
