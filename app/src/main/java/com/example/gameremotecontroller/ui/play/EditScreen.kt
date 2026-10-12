package com.example.gameremotecontroller.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.gameremotecontroller.model.ButtonType
import com.example.gameremotecontroller.model.GameProfile
import com.example.gameremotecontroller.model.PadButton
import com.example.gameremotecontroller.model.SUGGESTED_ACTIONS
import com.example.gameremotecontroller.model.VALID_CODES
import java.util.UUID

/**
 * EDIT: [배치] 패드 버튼 위치/크기 편집, [신호 매칭] 논리액션↔PC코드 바인딩 + 버튼별 액션 지정.
 * GAMEPAD/KVM이 동일 bindings를 공유 (따로 매핑 없음).
 */
@Composable
fun EditScreen(
  profile: GameProfile?,
  onSave: (GameProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  var draft by remember(profile) { mutableStateOf(profile) }
  var tab by remember { mutableIntStateOf(0) }

  if (draft == null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("프로필 없음") }
    return
  }
  val cur = draft!!

  Column(modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
      Text("EDIT · ${cur.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
      Button(onClick = { onSave(cur) }) { Text("저장") }
    }
    TabRow(selectedTabIndex = tab) {
      Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("배치") })
      Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("신호 매칭") })
    }
    when (tab) {
      0 ->
        LayoutTab(
          profile = cur,
          onChange = { draft = it },
          modifier = Modifier.weight(1f),
        )
      else ->
        MappingTab(
          profile = cur,
          onChange = { draft = it },
          modifier = Modifier.weight(1f),
        )
    }
  }
}

// ---------------------------------------------------------------- 배치 탭
@Composable
private fun LayoutTab(
  profile: GameProfile,
  onChange: (GameProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedId by remember(profile.name) { mutableStateOf(profile.buttons.firstOrNull()?.id) }
  val selected = profile.buttons.firstOrNull { it.id == selectedId }

  // 방향별 분할 배치. 오버레이로 덮으면(특히 가로) 버튼이 가려져 편집 불가.
  // 세로=위아래(캔버스+하단판), 가로=좌우(캔버스+우측 280dp판). 캔버스는 항상 전부 보임.
  BoxWithConstraints(modifier.fillMaxSize()) {
    if (maxWidth > maxHeight) {
      Row(Modifier.fillMaxSize()) {
        LayoutCanvas(
          profile = profile,
          selectedId = selectedId,
          onSelect = { selectedId = it },
          onChange = onChange,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        LayoutPanel(
          profile = profile,
          selected = selected,
          onSelectNew = { selectedId = it },
          onChange = onChange,
          modifier = Modifier.width(280.dp).fillMaxHeight(),
        )
      }
    } else {
      Column(Modifier.fillMaxSize()) {
        LayoutCanvas(
          profile = profile,
          selectedId = selectedId,
          onSelect = { selectedId = it },
          onChange = onChange,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        LayoutPanel(
          profile = profile,
          selected = selected,
          onSelectNew = { selectedId = it },
          onChange = onChange,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

// 캔버스: GamePadScreen과 동일 fraction 좌표계
@Composable
private fun LayoutCanvas(
  profile: GameProfile,
  selectedId: String?,
  onSelect: (String) -> Unit,
  onChange: (GameProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier
      .background(MaterialTheme.colorScheme.surfaceVariant),
  ) {
      val density = LocalDensity.current
      val wPx = constraints.maxWidth.toFloat()
      val hPx = constraints.maxHeight.toFloat()
      val shortSide = minOf(wPx, hPx)
      // 드래그 블록은 키가 안 바뀌면 재시작 안 되므로, 최신 draft는 State로 참조
      // (직접 캡처하면 오래된 좌표 기준으로 덮어써서 튐/제자리걸음 발생)
      val latest by rememberUpdatedState(profile)
      profile.buttons.forEach { btn ->
        val dia = (btn.size * shortSide).coerceAtLeast(with(density) { 40.dp.toPx() })
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier
              .offset(x = with(density) { (btn.x * wPx).toDp() }, y = with(density) { (btn.y * hPx).toDp() })
              .size(with(density) { dia.toDp() })
              .clip(CircleShape)
              .background(
                if (btn.id == selectedId) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer,
              )
              .then(
                if (btn.id == selectedId) {
                  Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                  Modifier
                },
              )
              .pointerInput(btn.id, wPx, hPx) {
                awaitEachGesture {
                  val down = awaitFirstDown()
                  onSelect(btn.id)
                  var px = down.position
                  while (true) {
                    val event = awaitPointerEvent()
                    // 다른 손가락 이벤트에는 우리 id가 없음 — 종료하지 말고 대기
                    val change = event.changes.find { it.id == down.id } ?: continue
                    if (!change.pressed) break
                    val dx = (change.position.x - px.x) / wPx
                    val dy = (change.position.y - px.y) / hPx
                    px = change.position
                    change.consume()
                    val base = latest
                    onChange(
                      base.copy(
                        buttons =
                          base.buttons.map {
                            if (it.id == btn.id) {
                              it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f))
                            } else {
                              it
                            }
                          },
                      ),
                    )
                  }
                }
              },
        ) { Text(btn.label, style = MaterialTheme.typography.labelSmall) }
      }
    }
}

// 조작판: 세로형 스택이라 가로 280dp에서도 잘리지 않음. 접기로 캔버스 확보.
@Composable
private fun LayoutPanel(
  profile: GameProfile,
  selected: PadButton?,
  onSelectNew: (String) -> Unit,
  onChange: (GameProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  var newLabel by remember { mutableStateOf("") }
  var newType by remember { mutableStateOf(ButtonType.TAP) }
  var collapsed by remember { mutableStateOf(false) }
  Column(
    modifier
      .verticalScroll(rememberScrollState())
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "버튼을 드래그해 배치, 탭해 선택",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = { collapsed = !collapsed }) { Text(if (collapsed) "펴기" else "접기") }
    }
    if (!collapsed) {
      Spacer(Modifier.height(6.dp))
      if (selected != null) {
        Text("선택: ${selected.label} (${selected.type}) x=${"%.2f".format(selected.x)} y=${"%.2f".format(selected.y)}")
        Text("크기", style = MaterialTheme.typography.bodySmall)
        Slider(
          value = selected.size,
          onValueChange = { s ->
            onChange(
              profile.copy(buttons = profile.buttons.map { if (it.id == selected.id) it.copy(size = s) else it }),
            )
          },
          valueRange = if (selected.type == ButtonType.JOYSTICK) 0.15f..0.60f else 0.08f..0.30f,
          modifier = Modifier.fillMaxWidth(),
        )
        Row {
          OutlinedButton(
            onClick = {
              onChange(
                profile.copy(buttons = profile.buttons.map { if (it.id == selected.id) it.copy(type = nextType(selected.type)) else it }),
              )
            },
            modifier = Modifier.weight(1f),
          ) { Text(selected.type.name) }
          OutlinedButton(
            onClick = { onChange(profile.copy(buttons = profile.buttons.filterNot { it.id == selected.id })) },
            modifier = Modifier.weight(1f),
          ) { Text("삭제") }
        }
      }
      Spacer(Modifier.height(6.dp))
      OutlinedTextField(
        value = newLabel,
        onValueChange = { newLabel = it },
        label = { Text("새 버튼 라벨") },
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(6.dp))
      Row {
        OutlinedButton(
          onClick = { newType = nextType(newType) },
          modifier = Modifier.weight(1f),
        ) { Text(newType.name) }
        Button(
          onClick = {
            val b =
              PadButton(
                id = UUID.randomUUID().toString(),
                label = newLabel.ifBlank { if (newType == ButtonType.JOYSTICK) "스틱" else "새버튼" },
                action = "ATTACK",
                type = newType,
                x = 0.4f,
                y = 0.4f,
                size = if (newType == ButtonType.JOYSTICK) 0.40f else 0.14f,
              )
            onChange(profile.copy(buttons = profile.buttons + b))
            onSelectNew(b.id)
            newLabel = ""
          },
          modifier = Modifier.weight(1f),
        ) { Text("추가") }
      }
    }
  }
}

private fun nextType(t: ButtonType): ButtonType =
  when (t) {
    ButtonType.TAP -> ButtonType.HOLD
    ButtonType.HOLD -> ButtonType.TOGGLE
    ButtonType.TOGGLE -> ButtonType.JOYSTICK
    else -> ButtonType.TAP
  }

// ---------------------------------------------------------------- 신호 매칭 탭
@Composable
private fun MappingTab(
  profile: GameProfile,
  onChange: (GameProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  var newAction by remember { mutableStateOf("") }
  var newCode by remember { mutableStateOf("Ctrl") }

  LazyColumn(modifier.padding(12.dp)) {
    item {
      Text("GAMEPAD·KVM 공통 바인딩", style = MaterialTheme.typography.titleSmall)
      Text("서버가 모르는 코드는 드롭됨. 목록 안에서 선택 권장.", style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(6.dp))
    }
    items(profile.bindings.entries.sortedBy { it.key }, key = { it.key }) { (action, code) ->
      var codeSel by remember(action, code) { mutableStateOf(code) }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(action, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        CodeDropdown(
          selected = codeSel,
          onSelect = { c ->
            codeSel = c
            onChange(profile.copy(bindings = profile.bindings + (action to c)))
          },
        )
        OutlinedButton(onClick = { onChange(profile.copy(bindings = profile.bindings - action)) }) {
          Text("삭제")
        }
      }
    }
    item {
      Spacer(Modifier.height(8.dp))
      Text("바인딩 추가", style = MaterialTheme.typography.titleSmall)
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = newAction,
          onValueChange = { newAction = it },
          label = { Text("액션명 (예: SKILL_7)") },
          modifier = Modifier.weight(1f),
        )
        CodeDropdown(selected = newCode, onSelect = { newCode = it })
      }
      Button(
        onClick = {
          if (newAction.isNotBlank()) {
            onChange(profile.copy(bindings = profile.bindings + (newAction.trim() to newCode)))
            newAction = ""
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) { Text("추가") }
      Spacer(Modifier.height(12.dp))
      Text("버튼별 액션 지정", style = MaterialTheme.typography.titleSmall)
    }
    items(profile.buttons, key = { it.id }) { btn ->
      var actSel by remember(btn.id, btn.action) { mutableStateOf(btn.action) }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(btn.label, modifier = Modifier.weight(0.6f))
        if (btn.type == ButtonType.JOYSTICK) {
          Text(
            "방향 고정(MOVE_*)",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          ActionDropdown(
            selected = actSel,
            options = (profile.bindings.keys + SUGGESTED_ACTIONS).distinct().sorted(),
            onSelect = { a ->
              actSel = a
              onChange(profile.copy(buttons = profile.buttons.map { if (it.id == btn.id) it.copy(action = a) else it }))
            },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
    item {
      Spacer(Modifier.height(8.dp))
      Text("마우스 감도", style = MaterialTheme.typography.titleSmall)
      Slider(
        value = profile.mouseSensitivity,
        onValueChange = { onChange(profile.copy(mouseSensitivity = it)) },
        valueRange = 0.2f..3.0f,
      )
    }
  }
}

// ---------------------------------------------------------------- 드롭다운
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeDropdown(
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
    val invalid = selected !in VALID_CODES
    OutlinedTextField(
      value = selected,
      onValueChange = {},
      readOnly = true,
      label = { Text(if (invalid) "코드(직접입력?)" else "코드") },
      isError = invalid,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.menuAnchor().size(width = 150.dp, height = 60.dp),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      VALID_CODES.forEach { c ->
        DropdownMenuItem(text = { Text(c) }, onClick = { onSelect(c); expanded = false })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionDropdown(
  selected: String,
  options: List<String>,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
    OutlinedTextField(
      value = selected,
      onValueChange = {},
      readOnly = true,
      label = { Text("액션") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.menuAnchor().fillMaxWidth(),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { a ->
        DropdownMenuItem(text = { Text(a) }, onClick = { onSelect(a); expanded = false })
      }
    }
  }
}
