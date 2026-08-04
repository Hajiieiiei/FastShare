package com.fastshare.app.protocol

import com.fastshare.app.data.network.protocol.DiscoveryPacket
import com.fastshare.app.data.network.protocol.HelloRequest
import com.fastshare.app.data.network.protocol.HelloResponse
import com.fastshare.app.data.network.protocol.Protocol
import com.fastshare.app.data.network.protocol.TransferRequest
import com.fastshare.app.data.network.protocol.TransferResponse
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DeviceType
import com.fastshare.app.domain.model.PayloadKind
import com.fastshare.app.domain.model.TransferItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProtocolSerializationTest {
    @Test
    fun `hello round-trips`() {
        val req = HelloRequest(
            protocolVersion = 1,
            deviceId = "abc-123",
            deviceName = "Pixel 8",
            platform = DevicePlatform.ANDROID,
            deviceType = DeviceType.PHONE,
            appVersion = "1.0.0",
            publicKey = "pubkey",
            fingerprint = "ABCD",
            capabilities = listOf("resume", "folders"),
            nonce = "abc",
        )
        val json = Protocol.json.encodeToString(HelloRequest.serializer(), req)
        val back = Protocol.json.decodeFromString(HelloRequest.serializer(), json)
        assertThat(back).isEqualTo(req)
    }

    @Test
    fun `unknown capabilities do not break deserialization`() {
        val json = """{"protocolVersion":1,"deviceId":"abc","deviceName":"x","platform":"android","deviceType":"phone","appVersion":"1","publicKey":"k","fingerprint":"f","capabilities":["bogus_cap","resume"],"nonce":"n"}"""
        val back = Protocol.json.decodeFromString(HelloRequest.serializer(), json)
        assertThat(back.deviceId).isEqualTo("abc")
    }

    @Test
    fun `transfer request encodes items`() {
        val items = listOf(
            TransferItem(id = "1", name = "photo.jpg", size = 12345L, mimeType = "image/jpeg", kind = PayloadKind.IMAGE),
            TransferItem(id = "2", name = "doc.pdf", size = 67890L, mimeType = "application/pdf", kind = PayloadKind.DOCUMENT),
        )
        val req = TransferRequest(
            sessionId = "s1", senderDeviceId = "d1", senderName = "Alice",
            items = items, totalSize = 80235L, manifestChecksum = "sha",
        )
        val json = Protocol.json.encodeToString(TransferRequest.serializer(), req)
        assertThat(json).contains("photo.jpg")
        assertThat(json).contains("doc.pdf")
    }

    @Test
    fun `discovery packet encodes compactly`() {
        val packet = DiscoveryPacket(
            type = DiscoveryPacket.PacketType.ANNOUNCE,
            deviceId = "d1",
            deviceName = "Pixel",
            platform = DevicePlatform.ANDROID,
            deviceType = DeviceType.PHONE,
            appVersion = "1.0",
            port = 53319,
            fingerprint = "DEADBEEF",
            capabilities = listOf("resume", "folders"),
            usesTls = true,
        )
        val json = Protocol.json.encodeToString(DiscoveryPacket.serializer(), packet)
        assertThat(json).contains("""n":"Pixel""")
        assertThat(json).contains("""p":53319""")
    }

    @Test
    fun `platform from wire handles unknown value`() {
        assertThat(DevicePlatform.fromWire("android")).isEqualTo(DevicePlatform.ANDROID)
        assertThat(DevicePlatform.fromWire("macos")).isEqualTo(DevicePlatform.MACOS)
        assertThat(DevicePlatform.fromWire(null)).isEqualTo(DevicePlatform.UNKNOWN)
        assertThat(DevicePlatform.fromWire("plan9")).isEqualTo(DevicePlatform.UNKNOWN)
    }
}
