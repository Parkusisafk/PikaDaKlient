#NoEnv
#Warn
SendMode Input
SetWorkingDir %A_ScriptDir%

; --- Settings ---
MinecraftTitle := "Minecraft"
Toggle := 0
; 15 seconds refresh rate in milliseconds (15 * 1000)
RefreshInterval := 15000
; Time to wait between Release (U) and Press (D) to ensure the game registers the refresh
ReleaseDuration := 50 


; --- Core Logic ---

F3::
    Toggle := !Toggle
    If (Toggle) {
        ; Start the constant hold timer (10ms)
        SetTimer, BackgroundClick, 10
        
        ; Start the 15-second refresh timer
        SetTimer, ClickRefresher, %RefreshInterval% 
        
        ToolTip, Auto-Clicker: ON (Refreshes every 15s)
    } Else {
        ; Stop both timers
        SetTimer, BackgroundClick, Off
        SetTimer, ClickRefresher, Off
        
        ; Ensure the mouse button is released for the final time
        ControlClick, x0 y0, %MinecraftTitle%, , Left, 1, U
        
        ToolTip, Auto-Clicker: OFF
    }
    ; Set the timer to remove the tooltip after 3 seconds (runs once).
    SetTimer, RemoveToolTip, -3000
Return

; Timer 1 (10ms): Ensures the click state is held down constantly for maximum stability.
BackgroundClick:
    ; 'D' means Down.
    ControlClick, x0 y0, %MinecraftTitle%, , Left, 1, D
Return


; Timer 2 (15s): Manages the brief release/re-hold cycle for input stability.
ClickRefresher:
    ; 1. Pause the constant 10ms "Down" command spam
    SetTimer, BackgroundClick, Off
    
    ; 2. Send the RELEASE (Up) command
    ControlClick, x0 y0, %MinecraftTitle%, , Left, 1, U
    
    ; 3. Wait 50ms to ensure the release registers in the game
    Sleep, %ReleaseDuration% 
    
    ; 4. Send the HOLD (Down) command again
    ControlClick, x0 y0, %MinecraftTitle%, , Left, 1, D
    
    ; 5. Resume the constant 10ms "Down" command spam for stability
    SetTimer, BackgroundClick, 10 
Return

RemoveToolTip:
    ToolTip
Return
