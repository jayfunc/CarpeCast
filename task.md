# Task Checklist: Implement Timeline Seek Control

## Protocol
- [ ] Add `SEEK:<position_ms>` command format to the UDP command protocol.

## Windows Subagent
- [x] Update `PlayerViewModel.cs`: Add `SeekCommand` (ICommand) to send `SEEK:<position_ms>` via NetworkService.
- [x] Update `NetworkService.cs`: Add a method to send `SEEK:<position_ms>` to the active device.
- [x] Update `PlayerPage.xaml`: Replace `ProgressBar` with a `Slider` representing progress in milliseconds. Bind it to the ViewModel and use `SeekCommand`.
- [x] Update `SmtcService.cs`: Enable `IsPositionEnabled` in SMTC and listen for `PositionChangeRequested` to trigger a seek.
