package io.padium.utils.http

import org.junit.Test
import org.junit.Assert.*

import java.net.URL
import java.util.concurrent.TimeUnit

class HttpUtilsTest {
    companion object {
        private const val KEY =
                "-----BEGIN PRIVATE KEY-----\n" +
                "MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDD/DQsDO4AMBQVQXBgr\n" +
                "yj9mVMGwIB5yZFZ8UP8KoJY5RRg449nywkaEknKZCU39bC6hZANiAASjmEGpF7hg\n" +
                "t2VFG3H4/9Lg1Hzn5jysVJR1cVj5McxaAnzcZooSCejwFdWd1Di50sNlIk97NvWF\n" +
                "wsIERRB0zWvbGQ/fMa0T3z7qEND4vblsRwuj5fTTJzola5pXMvqSO78=\n" +
                "-----END PRIVATE KEY-----"

        private const val CERT =
                "-----BEGIN CERTIFICATE-----\n" +
                "MIICnjCCAiSgAwIBAgIJAJ5PgZylmuUkMAoGCCqGSM49BAMCMIGlMQswCQYDVQQG\n" +
                "EwJVUzETMBEGA1UECAwKTmV3IEplcnNleTEQMA4GA1UEBwwHSG9ib2tlbjEYMBYG\n" +
                "A1UECgwPUGFkaXVtIExhYnMgTExDMRkwFwYDVQQLDBBMaW5jIEVuZ2luZWVyaW5n\n" +
                "MRkwFwYDVQQDDBBoYXlkZW4ucGFkaXVtLmlvMR8wHQYJKoZIhvcNAQkBFhBoYXlk\n" +
                "ZW5AcGFkaXVtLmlvMB4XDTE5MDUwMjExMTQ1NVoXDTIwMDUwMTExMTQ1NVowgaUx\n" +
                "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApOZXcgSmVyc2V5MRAwDgYDVQQHDAdIb2Jv\n" +
                "a2VuMRgwFgYDVQQKDA9QYWRpdW0gTGFicyBMTEMxGTAXBgNVBAsMEExpbmMgRW5n\n" +
                "aW5lZXJpbmcxGTAXBgNVBAMMEGhheWRlbi5wYWRpdW0uaW8xHzAdBgkqhkiG9w0B\n" +
                "CQEWEGhheWRlbkBwYWRpdW0uaW8wdjAQBgcqhkjOPQIBBgUrgQQAIgNiAASjmEGp\n" +
                "F7hgt2VFG3H4/9Lg1Hzn5jysVJR1cVj5McxaAnzcZooSCejwFdWd1Di50sNlIk97\n" +
                "NvWFwsIERRB0zWvbGQ/fMa0T3z7qEND4vblsRwuj5fTTJzola5pXMvqSO7+jHjAc\n" +
                "MBoGA1UdEQQTMBGCCWxvY2FsaG9zdIcEwKgBpDAKBggqhkjOPQQDAgNoADBlAjEA\n" +
                "7Bk1BTVa/Q4OVikWKH0xskog+6J2gbER1591/b4hSNbH+ZnhVpAhaZk0goaJDaLM\n" +
                "AjAZYnVAt6OIlJs4lvQ3K0iqF9keJJEdd4j9ktMEbF49G11VxkDtTNHLFhXqdHCd\n" +
                "XOs=\n" +
                "-----END CERTIFICATE-----"

        private const val CA =
                "-----BEGIN CERTIFICATE-----\n" +
                "MIICnTCCAiSgAwIBAgIJAMVJvuCa14m4MAoGCCqGSM49BAMCMIGlMQswCQYDVQQG\n" +
                "EwJVUzETMBEGA1UECAwKTmV3IEplcnNleTEQMA4GA1UEBwwHSG9ib2tlbjEYMBYG\n" +
                "A1UECgwPUGFkaXVtIExhYnMgTExDMRkwFwYDVQQLDBBMaW5jIEVuZ2luZWVyaW5n\n" +
                "MRkwFwYDVQQDDBBoYXlkZW4ucGFkaXVtLmlvMR8wHQYJKoZIhvcNAQkBFhBoYXlk\n" +
                "ZW5AcGFkaXVtLmlvMB4XDTE5MDUwMjExMTYwNVoXDTIwMDUwMTExMTYwNVowgaUx\n" +
                "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApOZXcgSmVyc2V5MRAwDgYDVQQHDAdIb2Jv\n" +
                "a2VuMRgwFgYDVQQKDA9QYWRpdW0gTGFicyBMTEMxGTAXBgNVBAsMEExpbmMgRW5n\n" +
                "aW5lZXJpbmcxGTAXBgNVBAMMEGhheWRlbi5wYWRpdW0uaW8xHzAdBgkqhkiG9w0B\n" +
                "CQEWEGhheWRlbkBwYWRpdW0uaW8wdjAQBgcqhkjOPQIBBgUrgQQAIgNiAARJTcAM\n" +
                "/YtzLNnkqsKud8OmqnuuT4qcs3tVVjW8MyJ3vRni2YRSZy9llkSQfe76sz4xLiWw\n" +
                "2Dz9X9HZPZGoo/XOfWlj2Tke+i1stNl6R8ZqpyxSIbb06opFt3nqtkT0VB6jHjAc\n" +
                "MBoGA1UdEQQTMBGCCWxvY2FsaG9zdIcEwKgB6jAKBggqhkjOPQQDAgNnADBkAjB4\n" +
                "p0Wmhw261uOQIbzcz6q6k5x8CekMGVSxl0pD2nCe+dwblHpG4vL4xLuwOmPtoIcC\n" +
                "MA16LeytiVqUKrWxqDgZ6ButPWBIsrgGJNisaKBPpC/0EGF1R6f9XDdfydrNV2rC\n" +
                "ZA==\n" +
                "-----END CERTIFICATE-----\n"
    }

    @Test
    fun testRequestText() {
        try {
            val result = HttpUtils.requestText("https://www.google.com/", HttpMethod.GET)
            val response = result.get(10, TimeUnit.SECONDS)
            assertTrue(result.isDone)
            assertTrue(HttpUtils.isSuccess(response.first))
            assertFalse(HttpUtils.isError(response.first))
            assertFalse(HttpUtils.isInformational(response.first))
            assertFalse(HttpUtils.isRedirect(response.first))
            assertTrue(response.second?.isNotEmpty()!!)
            assertFalse(response.third.isEmpty())
        } catch(th: Throwable) {
            fail(th.message)
        }
    }

    @Test
    fun testRequestTextTls() {
        try {
            val result = HttpUtils.requestText("https://localhost:31337/devices", HttpMethod.GET,
                    ca = CA, cert = CERT, key = KEY)
            val response = result.get(10, TimeUnit.SECONDS)
            assertTrue(result.isDone)
            assertTrue(HttpUtils.isSuccess(response.first))
            assertFalse(HttpUtils.isError(response.first))
            assertFalse(HttpUtils.isInformational(response.first))
            assertFalse(HttpUtils.isRedirect(response.first))
            assertTrue(response.second?.isNotEmpty()!!)
            assertFalse(response.third.isEmpty())
        } catch(th: Throwable) {
            fail(th.message)
        }
    }

    @Test
    fun testCompareURL() {
        //Basic
        assertTrue(URL("https://padium.io") == URL("https://padium.io"))
        assertFalse(URL("https://padium.io") == URL("http://padium.io"))

        //Ports
        assertTrue(URL("https://padium.io:443") == URL("https://padium.io"))
        assertTrue(URL("http://padium.io:80") == URL("http://padium.io"))
        assertFalse(URL("https://padium.io:80") == URL("http://padium.io:80"))
    }
}
