package com.metrolist.desktop.media

import com.metrolist.desktop.stream.ResolvedAudio
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration

class ResolvedMediaFetcher(
    private val client: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
    private val writer: ValidatedMediaWriter = ValidatedMediaWriter(),
) {
    fun fetch(
        resolved: ResolvedAudio,
        destination: Path,
    ): WrittenMedia {
        val request =
            HttpRequest.newBuilder(URI.create(resolved.url))
                .timeout(Duration.ofSeconds(45))
                .GET()
                .apply {
                    resolved.headers.forEach { (name, value) ->
                        header(name, value)
                    }
                }
                .build()

        val response =
            client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream(),
            )

        check(response.statusCode() == 200 || response.statusCode() == 206) {
            "Media transfer failed with HTTP ${response.statusCode()}"
        }

        response.body().use { input ->
            return writer.write(
                input = input,
                expectedLength = resolved.contentLength,
                destination = destination,
            )
        }
    }
}
