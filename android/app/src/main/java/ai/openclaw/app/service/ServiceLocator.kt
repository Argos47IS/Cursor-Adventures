package ai.openclaw.app.service

import ai.openclaw.app.OpenClawApplication
import ai.openclaw.app.commands.NodeCommandHandler
import ai.openclaw.app.crypto.DeviceIdentity
import ai.openclaw.app.discovery.GatewayDiscovery
import ai.openclaw.app.network.GatewayClient

object ServiceLocator {

    val deviceIdentity: DeviceIdentity by lazy {
        DeviceIdentity(OpenClawApplication.instance.securePreferences)
    }

    val gatewayClient: GatewayClient by lazy {
        GatewayClient(
            OpenClawApplication.instance.securePreferences,
            deviceIdentity,
        )
    }

    val gatewayDiscovery: GatewayDiscovery by lazy {
        GatewayDiscovery(OpenClawApplication.instance)
    }

    val nodeCommandHandler: NodeCommandHandler by lazy {
        NodeCommandHandler(
            OpenClawApplication.instance,
            gatewayClient,
        )
    }
}
