package ani.saikou.neo.extension.api

/** One request for [HostServices.http]. */
public data class HostRequest(
    val url: String,
    val method: HostMethod = HostMethod.Get,
    val headers: Map<String, String> = emptyMap(),
    /** The body to send, for a [HostMethod.Post]. */
    val body: String? = null,
    /** The body's content type, such as "application/json". */
    val contentType: String? = null,
) {
    /** Leaves out header values and the body, which often hold cookies or tokens. */
    override fun toString(): String =
        "HostRequest(url=$url, method=$method, headers=${headers.keys}, " +
            "body=${if (body == null) "null" else "<hidden>"}, contentType=$contentType)"
}

public enum class HostMethod { Get, Post }

/** The answer to a [HostRequest]. */
public data class HostResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
) {
    /** Leaves out header values and the body, which often hold cookies or tokens. */
    override fun toString(): String = "HostResponse(status=$status, headers=${headers.keys}, body=<${body.length} chars>)"
}
