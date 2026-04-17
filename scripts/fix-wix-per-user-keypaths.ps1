[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [string]$RegistryRoot = "HKCU",
    [string]$RegistryKey = "Software\sywyar\PixivDownload\Components"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Path)) {
    throw "WiX source file not found: $Path"
}

$nsUri = "http://schemas.microsoft.com/wix/2006/wi"
$xml = New-Object System.Xml.XmlDocument
$xml.PreserveWhitespace = $true
$xml.Load($Path)

$namespaceManager = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
$namespaceManager.AddNamespace("w", $nsUri)
$components = $xml.SelectNodes("//w:Component", $namespaceManager)
foreach ($component in $components) {
    $componentId = $component.GetAttribute("Id")
    $targetRegistryValue = $component.SelectSingleNode(
        "./w:RegistryValue[@Root='$RegistryRoot' and @Key='$RegistryKey' and @Name='$componentId']",
        $namespaceManager
    )
    $fileKeyPath = $component.SelectSingleNode("./w:File[@KeyPath='yes']", $namespaceManager)
    if ($targetRegistryValue -and $targetRegistryValue.GetAttribute("KeyPath") -eq "yes" -and -not $fileKeyPath) {
        continue
    }

    if (-not $fileKeyPath) {
        $fileKeyPath = $component.SelectSingleNode("./w:File[1]", $namespaceManager)
    }
    if (-not $fileKeyPath) {
        continue
    }

    if ($fileKeyPath.HasAttribute("KeyPath")) {
        $null = $fileKeyPath.RemoveAttribute("KeyPath")
    }

    if ($targetRegistryValue) {
        $null = $component.RemoveChild($targetRegistryValue)
    }

    $registryValue = $xml.CreateElement("RegistryValue", $nsUri)
    $null = $registryValue.SetAttribute("Root", $RegistryRoot)
    $null = $registryValue.SetAttribute("Key", $RegistryKey)
    $null = $registryValue.SetAttribute("Name", $componentId)
    $null = $registryValue.SetAttribute("Type", "integer")
    $null = $registryValue.SetAttribute("Value", "1")
    $null = $registryValue.SetAttribute("KeyPath", "yes")
    $null = $component.AppendChild($registryValue)
}

$settings = New-Object System.Xml.XmlWriterSettings
$settings.Indent = $true
$settings.IndentChars = "    "
$settings.Encoding = New-Object System.Text.UTF8Encoding($false)

$writer = [System.Xml.XmlWriter]::Create($Path, $settings)
try {
    $xml.Save($writer)
} finally {
    $writer.Dispose()
}
