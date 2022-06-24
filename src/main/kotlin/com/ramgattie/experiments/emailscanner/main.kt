package com.ramgattie.experiments.emailscanner
import java.io.*
import com.google.gson.Gson
import org.apache.commons.validator.routines.EmailValidator
import org.xbill.DNS.Name
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import org.xbill.DNS.lookup.LookupResult
import org.xbill.DNS.lookup.LookupSession
import org.xbill.DNS.lookup.NoSuchDomainException
import java.net.IDN
import java.util.concurrent.ExecutionException

class Main {
    enum class ValidationResultEnum(validationResult: Int) {
        VALID_EMAIL_ADDRESS(0),
        INVALID_EMAIL_ADDRESS(1),
        COMMON_DOMAIN_TYPO(2),
        TEMPORARY_DOMAIN_NAME(3),
        NO_MX_OR_A_RECORD(4),
    }
    fun checkDNSRecordsMaybeUpdateCache(
        emailAddressDomain: String,
        recordType: Int,
    ): (Boolean) {
        var hasDNSRecord = false
        val recordTypeAsString = Type.string((recordType))
        try {
            val lookUpResolver = SimpleResolver("1.1.1.1") //Use Cloudflare DNS
            val lookupSessionBuilder = LookupSession.defaultBuilder()
            lookupSessionBuilder.resolver(lookUpResolver)
            val lookupSession = lookupSessionBuilder.build()
            val dnsLookup = Name.fromString(emailAddressDomain)
            lookupSession.lookupAsync(dnsLookup, recordType)
                .whenComplete { answers: LookupResult, ex: Throwable? ->
                    if (ex == null && answers.records.isNotEmpty()) {
                        hasDNSRecord = true
                    }
                }
                .toCompletableFuture()
                .get()
        } catch (ex: NoSuchDomainException) {
            println("$emailAddressDomain caused an ExecutionException and the cause was ${ex.cause} in $recordTypeAsString Scan")
        } catch (ex: ExecutionException) {
            println("$emailAddressDomain caused an ExecutionException and the cause was ${ex.cause} in $recordTypeAsString Scan")
        }
        return hasDNSRecord
    }

    fun hasMXDNSRecords(emailAddressDomain: String): (Boolean) {
        return checkDNSRecordsMaybeUpdateCache(
            emailAddressDomain,
            Type.MX,
        )
    }

    fun hasADNSRecord(emailAddressDomain: String): Boolean {
        return checkDNSRecordsMaybeUpdateCache(
            emailAddressDomain,
            Type.A,
        )
    }

    fun isTemporaryEmailAddressDomain(emailAddressDomain: String): Boolean {
        return emailAddressDomain in File("DisposableEmailDomains.txt").readLines()
    }

    fun quickScan(emailAddress: String): (ValidationResultEnum) {
        val apacheEmailValidator = EmailValidator.getInstance(false, false)
        val isValidEmail = apacheEmailValidator.isValid(emailAddress)
        return if (!isValidEmail) {
            ValidationResultEnum.INVALID_EMAIL_ADDRESS
        } else {
            val emailAddressDomain =
                IDN.toASCII(emailAddress.substring(emailAddress.indexOf('@') + 1)).lowercase() // Convert domain to punycode and then lowercase
            if (isTemporaryEmailAddressDomain(emailAddressDomain)){
                ValidationResultEnum.TEMPORARY_DOMAIN_NAME
            } else if (!hasMXDNSRecords(emailAddressDomain) && !hasADNSRecord(emailAddressDomain)) { // Only true when there is no MX and no A records
                ValidationResultEnum.NO_MX_OR_A_RECORD
            } else {
                ValidationResultEnum.VALID_EMAIL_ADDRESS
            }
        }
    }


    fun handler(input: InputStream, output: OutputStream): Unit {
        try {
            val inputAsString = input.bufferedReader().use { it.readText() }
            var lambdaMapObj: Map<String, Any> = HashMap()
            lambdaMapObj = Gson().fromJson(inputAsString, lambdaMapObj.javaClass)
            val inputParams = lambdaMapObj["body"].toString()
            var requestBodyMapObj: Map<String, Any> = HashMap()
            requestBodyMapObj = Gson().fromJson(inputParams, requestBodyMapObj.javaClass)
            val emailAddress = requestBodyMapObj["email_address"].toString()
            val quickScanResult = quickScan(emailAddress)
            println(quickScanResult)

            val gson = Gson()
            val resMap: HashMap<String, String> = hashMapOf("results" to quickScanResult.toString())
            output.write(gson.toJson(resMap).toString().toByteArray())
        } catch (e: java.lang.Exception){
            println(e)
            val gson = Gson()
            val resMap: HashMap<String, String> = hashMapOf("error" to "Please report this error to the site owner")
            output.write(gson.toJson(resMap).toString().toByteArray())
        }
    }
}