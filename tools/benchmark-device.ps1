function Get-BenchmarkDeviceKey {
    param(
        [Parameter(Mandatory = $true)][string]$Manufacturer,
        [Parameter(Mandatory = $true)][string]$Model,
        [Parameter(Mandatory = $true)][string]$Sdk
    )

    $identity = @(
        $Manufacturer.Trim().ToLowerInvariant()
        $Model.Trim().ToLowerInvariant()
        $Sdk.Trim()
    ) -join "|"
    $bytes = [Text.Encoding]::UTF8.GetBytes($identity)
    $digest = [Security.Cryptography.SHA256]::HashData($bytes)
    $hex = [BitConverter]::ToString($digest).Replace("-", "").ToLowerInvariant()
    return "sha256:$hex"
}

function Get-BenchmarkDeviceMetadata {
    $manufacturer = (Invoke-AdbText shell getprop ro.product.manufacturer).Trim()
    $model = (Invoke-AdbText shell getprop ro.product.model).Trim()
    $device = (Invoke-AdbText shell getprop ro.product.device).Trim()
    $androidRelease = (Invoke-AdbText shell getprop ro.build.version.release).Trim()
    $sdk = (Invoke-AdbText shell getprop ro.build.version.sdk).Trim()

    return [ordered]@{
        deviceKey = Get-BenchmarkDeviceKey -Manufacturer $manufacturer -Model $model -Sdk $sdk
        manufacturer = $manufacturer
        model = $model
        device = $device
        androidRelease = $androidRelease
        sdk = $sdk
    }
}
