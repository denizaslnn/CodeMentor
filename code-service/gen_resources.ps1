$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$trContent = @"
error.user.notfound=Kullanıcı bulunamadı: {0}
error.task.notfound=Görev bulunamadı: {0}
error.auth.invalid=Geçersiz kullanıcı adı veya şifre
error.auth.refreshtoken.missing=Refresh token eksik
error.auth.refreshtoken.invalid=Geçersiz refresh token
error.auth.refreshtoken.expired=Refresh token süresi dolmuş
error.user.alreadyexists=Kullanıcı adı zaten kullanımda
success.analyze.queued=Analiz sıraya alındı
validation.username.required=Kullanıcı adı boş olamaz
validation.password.required=Şifre boş olamaz
validation.password.size=Şifre boyutu geçersiz
validation.password.pattern=Şifre formatı geçersiz
error.resource.notfound=Kaynak bulunamadı
error.generic.invalid.request=Geçersiz istek
error.generic.conflict=Kaynak çakışması
error.generic.database=Veritabanı hatası oluştu
error.generic.unexpected=Beklenmeyen bir hata oluştu
"@

$enContent = @"
error.user.notfound=User not found: {0}
error.task.notfound=Analysis task not found: {0}
error.auth.invalid=Invalid username or password
error.auth.refreshtoken.missing=Refresh token is missing
error.auth.refreshtoken.invalid=Invalid refresh token
error.auth.refreshtoken.expired=Refresh token has expired
error.user.alreadyexists=Username already exists
success.analyze.queued=Analysis queued successfully
validation.username.required=Username is required
validation.password.required=Password is required
validation.password.size=Invalid password length
validation.password.pattern=Invalid password format
error.resource.notfound=Resource not found
error.generic.invalid.request=Invalid request
error.generic.conflict=Resource conflict
error.generic.database=A database error occurred
error.generic.unexpected=An unexpected error occurred
"@

[System.IO.File]::WriteAllText("src/main/resources/messages_tr.properties", $trContent, $utf8NoBom)
[System.IO.File]::WriteAllText("src/main/resources/messages_en.properties", $enContent, $utf8NoBom)

Write-Output "Files written."

# Verify BOM is absent
$bytes = [System.IO.File]::ReadAllBytes("src/main/resources/messages_tr.properties")
Write-Output ("First 3 bytes: " + ($bytes[0..2] -join '-'))