param (
    [string]$ServiceName  = "ZillWrapper",
    [string]$JavaExe      = "C:\Program Files\OpenJDK\jdk-25\bin\java.exe",
    [string]$AppDir       = "C:\ZillWrapper",
    [string]$AppJar       = "C:\ZillWrapper\ZillWrapper-0.0.3.jar",
    [string]$TrustStore   = "C:\cert\truststore.jks",
    [string]$TrustPass    = "<TRUSTSTORE_PASSWORD>"
)

# DB / APP secrets (остальные внутренние переменные)
$DbUrl         = "<DB_URL>"
$DbUser        = "<DB_USER>"
$DbPass        = "<DB_PASSWORD>"
$AppMasterKey  = "<APP_MASTER_KEY>"

# Mail secrets/config
$MailHost      = "<MAIL_HOST>"
$MailPort      = "<MAIL_PORT>"
$MailProtocol  = "<MAIL_PROTOCOL>"
$MailUser      = "<MAIL_USER>"
$MailPass      = "<MAIL_PASSWORD>"

# Optional app env
$WhiteBaseUrl          = "<WHITE_BASE_URL>"
$WhiteLogin            = "<WHITE_LOGIN>"
$WhitePassword         = "<WHITE_PASSWORD>"
$WhiteAdminCreateUrl   = "<WHITE_ADMIN_CREATE_URL>"
$ZillToken             = "<ZILL_TOKEN>"
$ZillBotName           = "<ZILL_BOT_NAME>"
$DinoBaseUrl           = "<DINO_BASE_URL>"
$DinoLogin             = "<DINO_LOGIN>"
$DinoPassword          = "<DINO_PASSWORD>"

$JavaArgs = @(
    "-Djavax.net.ssl.trustStore=$TrustStore",
    "-Djavax.net.ssl.trustStorePassword=$TrustPass",
    "-DSPRING_MAIL_HOST=$MailHost",
    "-DSPRING_MAIL_PORT=$MailPort",
    "-DSPRING_MAIL_PROTOCOL=$MailProtocol",
    "-DSPRING_MAIL_USERNAME=$MailUser",
    "-DSPRING_MAIL_PASSWORD=$MailPass",
    "-DDB_URL=$DbUrl",
    "-DDB_USER=$DbUser",
    "-DDB_PASS=$DbPass",
    "-DAPP_MASTER_KEY=$AppMasterKey",
    "-DWHITE_ADMIN_BASE_URL=$WhiteBaseUrl",
    "-DWHITE_LOGIN=$WhiteLogin",
    "-DWHITE_PASSWORD=$WhitePassword",
    "-DWHITEADMIN_ORDER_CREATE_URL=$WhiteAdminCreateUrl",
    "-DZILL_TOKEN=$ZillToken",
    "-DZILL_BNAME=$ZillBotName",
    "-DDINO_BASE_URL=$DinoBaseUrl",
    "-DDINO_LOGIN=$DinoLogin",
    "-DDINO_PASSWORD=$DinoPassword",
    "-jar",
    $AppJar
)

& nssm remove $ServiceName confirm *>$null
& nssm install $ServiceName $JavaExe $JavaArgs
& nssm set $ServiceName AppDirectory $AppDir
& nssm set $ServiceName AppExit Default Exit
& nssm set $ServiceName AppStdout "$AppDir\stdout.log"
& nssm set $ServiceName AppStderr "$AppDir\stderr.log"

& nssm start $ServiceName