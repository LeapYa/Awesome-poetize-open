@echo off
rem Thin wrapper for the Poetize blog-automation CLI (Windows).
rem
rem Resolves its own directory via %~dp0 so it works from any CWD; the agent
rem never needs to pass baseDir. Counterpart of poetize-blog (bash) on macOS/Linux.
rem
rem Python is still required at runtime; we prefer the `py` launcher, then
rem python, then python3.
setlocal
set "CLI=%~dp0scripts\poetize_cli.py"

where py >nul 2>nul
if %errorlevel%==0 (
  py "%CLI%" %*
  exit /b %errorlevel%
)

where python >nul 2>nul
if %errorlevel%==0 (
  python "%CLI%" %*
  exit /b %errorlevel%
)

where python3 >nul 2>nul
if %errorlevel%==0 (
  python3 "%CLI%" %*
  exit /b %errorlevel%
)

echo error: python ^(py/python/python3^) is required to run the Poetize CLI >&2
exit /b 127
