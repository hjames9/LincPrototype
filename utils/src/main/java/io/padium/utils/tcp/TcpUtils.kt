package io.padium.utils.tcp

import io.vertx.core.AsyncResult
import io.vertx.core.Vertx
import io.vertx.core.VertxException
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetSocket
import io.vertx.kotlin.core.net.PfxOptions
import io.vertx.core.file.FileSystemException
import io.vertx.core.net.PemTrustOptions
import io.vertx.core.streams.Pump
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.Base64

object TcpUtils {
    private const val BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n"
    private const val END_CERTIFICATE = "-----END CERTIFICATE-----"
    private val LINE_SEPARATOR = System.getProperty("line.separator")

    private val vertx : Vertx

    init {
        System.setProperty("vertx.disableFileCPResolving", "true")
        System.setProperty("vertx.disableDnsResolver", "true")
        vertx = Vertx.vertx()
    }

    @Throws(TcpException::class)
    private fun setupTlsOptions(keysDirectory : File, clientOptions: NetClientOptions, useInternalCerts: Boolean = true)
            : NetClientOptions {
        clientOptions.isSsl = true

        try {
            //Client key store
            val keyStoreFile = "$keysDirectory/keystore.p12"
            val clientKeyStoreBuffer = vertx.fileSystem().readFileBlocking(keyStoreFile)
            val pfxOptions = PfxOptions(value = clientKeyStoreBuffer, password = "admin123")
            clientOptions.pfxKeyCertOptions = pfxOptions
        } catch (e: NoSuchFileException) {
            throw TcpException(e.message, e)
        } catch (e: FileSystemException) {
            throw TcpException(e.message, e)
        } catch(e: VertxException) {
            throw TcpException(e.message, e)
        } catch (e: IOException) {
            throw TcpException(e.message, e)
        }

        try {
            //Client trust store
            clientOptions.isTrustAll = false
            if(useInternalCerts) {
                val trustOptions = PemTrustOptions().addCertValue(Buffer.buffer(getAndroidRootCertificates()))
                clientOptions.pemTrustOptions = trustOptions
            } else {
                val trustStoreFile = "$keysDirectory/truststore.p12"
                val clientTrustStoreBuffer = vertx.fileSystem().readFileBlocking(trustStoreFile)
                val trustOptions = PfxOptions(value = clientTrustStoreBuffer, password = "admin123")
                clientOptions.pfxTrustOptions = trustOptions
            }
        } catch (e: NoSuchFileException) {
            throw TcpException(e.message, e)
        } catch (e: FileSystemException) {
            throw TcpException(e.message, e)
        } catch (e: CertificateException) {
            throw TcpException(e.message, e)
        } catch (e: KeyStoreException) {
            throw TcpException(e.message, e)
        } catch (e: NoSuchAlgorithmException) {
            throw TcpException(e.message, e)
        } catch(e: VertxException) {
            throw TcpException(e.message, e)
        } catch (e: IOException) {
            throw TcpException(e.message, e)
        }

        try {
            //Certificate revocation list
            val crlFile = "$keysDirectory/crl.pem"
            val crlABuffer = vertx.fileSystem().readFileBlocking(crlFile)
            clientOptions.crlValues.add(crlABuffer)
        } catch (e: NoSuchFileException) {
            throw TcpException(e.message, e)
        } catch (e: FileSystemException) {
            throw TcpException(e.message, e)
        } catch(e: VertxException) {
            throw TcpException(e.message, e)
        } catch (e: IOException) {
            throw TcpException(e.message, e)
        }

        return clientOptions
    }

    @Throws(TcpException::class)
    fun doTlsConnection(keyLocation : File, host : String, port : Int, callback: TcpCallback): TcpConnection {
        val clientOptions = setupTlsOptions(keyLocation, NetClientOptions())
        return doConnection(clientOptions, host, port, callback)
    }

    @Throws(TcpException::class)
    fun doTcpConnection(host : String, port : Int, callback: TcpCallback): TcpConnection {
        val clientOptions = NetClientOptions()
        return doConnection(clientOptions, host, port, callback)
    }

    @Throws(TcpException::class)
    private fun doConnection(clientOptions: NetClientOptions, host : String, port : Int,
                             callback: TcpCallback): TcpConnection {
        val client = vertx.createNetClient(clientOptions)
        val streams = TcpConnection(callback)

        val connHandler : (AsyncResult<NetSocket>) -> Unit = { res : AsyncResult<NetSocket> ->
            if(res.succeeded()) {
                val socket = res.result()
                Pump.pump(socket, socket).start()
                streams.setupSocket(socket)
                callback.onConnect(streams,true)
            } else {
                callback.onConnect(streams,false)
            }
        }

        client.connect(port, host, connHandler)
        return streams
    }

    @Throws(CertificateException::class, IOException::class, KeyStoreException::class, NoSuchAlgorithmException::class)
    private fun getAndroidRootCertificates(): String {
        //List Android default root certificates

        val sb = StringBuilder()
        val ks = KeyStore.getInstance("AndroidCAStore")

        if (ks != null) {
            ks.load(null, null)
            val aliases = ks.aliases()
            var newLine = ""
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement() as String
                val cert = ks.getCertificate(alias) as X509Certificate
                sb.append(newLine)
                newLine = "\n"
                sb.append(convertToPem(cert))
            }
        }

        return sb.toString()
    }

    @Throws(CertificateEncodingException::class)
    private fun convertToPem(cert: X509Certificate): String {
        val encoder = Base64.getMimeEncoder(64, LINE_SEPARATOR.toByteArray())
        val derCert = cert.encoded
        val pemCertPre = String(encoder.encode(derCert))
        return BEGIN_CERTIFICATE + pemCertPre + END_CERTIFICATE
    }
}