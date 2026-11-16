@echo off
REM Script day code len GitHub - JavaFTP-Client-Server
REM Chay trong thu muc goc du an (noi co .gitignore va README.md)

echo [1/6] Khoi tao git repository...
git init

echo [2/6] Them tat ca file vao staging...
git add .

echo [3/6] Commit...
git commit -m "Initial commit: JavaFTP Client & Server"

echo [4/6] Doi ten branch chinh thanh main...
git branch -M main

echo [5/6] Them remote origin...
git remote add origin https://github.com/Chamm286/JavaFTP-Client-Server.git

echo [6/6] Push len GitHub...
git push -u origin main

echo.
echo Hoan tat! Neu Terminal yeu cau dang nhap:
echo   - Username: nhap ten GitHub cua ban (Chamm286)
echo   - Password: nhap Personal Access Token (PAT), KHONG phai mat khau GitHub.
echo   Tao PAT tai: https://github.com/settings/tokens (scope: repo)
pause