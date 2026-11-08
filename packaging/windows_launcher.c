#define UNICODE
#define _UNICODE
#include <windows.h>
#include <wchar.h>
#include <stdlib.h>

/* Unicode-safe portable launcher. No system Java, shell, registry edits, or console window required. */
int WINAPI WinMain(HINSTANCE instance,HINSTANCE previous,LPSTR unused,int show) {
    (void)instance;(void)previous;(void)unused;(void)show;
    wchar_t root[32768],java[32768],command[32768];
    DWORD length=GetModuleFileNameW(NULL,root,32768);
    if(!length || length>=32767)return 1;
    wchar_t *slash=wcsrchr(root,L'\\');if(!slash)return 1;*slash=L'\0';
    const wchar_t *args=GetCommandLineW();
    if(*args==L'"'){args++;while(*args && *args!=L'"')args++;if(*args)args++;}
    else while(*args && *args!=L' ' && *args!=L'\t')args++;
    while(*args==L' ' || *args==L'\t')args++;
    int verify=wcsstr(args,L"--verify-install")!=NULL;
    if(swprintf(java,32768,L"%ls\\runtime\\bin\\%ls",root,verify?L"java.exe":L"javaw.exe")<0)return 1;
    if(GetFileAttributesW(java)==INVALID_FILE_ATTRIBUTES){
        MessageBoxW(NULL,L"The bundled Java runtime is missing. Extract the complete AstralCore download before launching.",L"AstralCore",MB_ICONERROR|MB_OK);return 1;
    }
    if(swprintf(command,32768,L"\"%ls\" -Dastral.packaged=true -Dfile.encoding=UTF-8 -jar \"%ls\\app\\AstralCore.jar\" %ls",java,root,args)<0)return 1;
    STARTUPINFOW startup={0};PROCESS_INFORMATION process={0};startup.cb=sizeof(startup);
    if(!CreateProcessW(java,command,NULL,NULL,FALSE,verify?CREATE_NO_WINDOW:0,NULL,root,&startup,&process)){
        MessageBoxW(NULL,L"AstralCore could not start. Extract the complete folder and try again.",L"AstralCore",MB_ICONERROR|MB_OK);return 1;
    }
    WaitForSingleObject(process.hProcess,INFINITE);DWORD status=1;GetExitCodeProcess(process.hProcess,&status);
    CloseHandle(process.hThread);CloseHandle(process.hProcess);return (int)status;
}
