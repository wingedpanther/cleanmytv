import { useEffect, useState, type ReactNode } from 'react'
import type { ScreenProps } from '../App'
import type { BootCheckResult } from '@shared/api'
import { Busy, errorText } from '../components/ui'

const STEPS = [
  'На телевизоре откройте «Для разработчиков» и отключите отладку по USB.',
  'Если на телевизоре есть отладка по сети, ADB или беспроводная отладка — отключите её.',
  'Дополнительно можно выбрать «Отозвать разрешения на отладку по USB», чтобы компьютер больше не считался доверенным. Не делайте этого, если не хотите снова подтверждать подключение в следующий раз.',
  'Перезапустите телевизор.'
]

export function Завершение({ status, markГотово }: ScreenProps): ReactNode {
  const [host, setHost] = useState(status.host ?? '')
  const [ticks, setTicks] = useState<boolean[]>(STEPS.map(() => false))
  const [checking, setChecking] = useState(false)
  const [res, setRes] = useState<{ closed: boolean; message: string } | null>(null)
  const [restarting, setПерезапуститьing] = useState(false)
  const [boot, setBoot] = useState<BootCheckResult | null>(null)
  const [sawДомой, setSawДомой] = useState(false)
  // Debugging is the only way back into a TV that boots to a black screen (#33): it stays on until a restart has worked.
  const safe = boot?.ok === true || sawДомой

  useEffect(() => {
    if (!host) window.cytv.lastHost().then((h) => h && setHost(h))
  }, [])

  async function check(): Promise<void> {
    setChecking(true)
    setRes(null)
    try {
      const r = await window.cytv.checkAdbClosed(host.trim())
      setRes(r)
      if (r.closed) markГотово('finish')
    } catch (e) {
      setRes({ closed: false, message: e instanceof Error ? e.message : String(e) })
    } finally {
      setChecking(false)
    }
  }

  async function restartCheck(): Promise<void> {
    setПерезапуститьing(true)
    setBoot(null)
    try {
      setBoot(await window.cytv.bootCheck())
    } catch (e) {
      setBoot({ ok: false, reconnected: false, summary: errorText(e) })
    } finally {
      setПерезапуститьing(false)
    }
  }

  return (
    <>
      <h1>Завершение and lock</h1>
      <p className="lede">
        Network debugging is a plain-text shell on port 5555. On some TVs any device on your Wi-Fi can use it without the Allow
        prompt. Close it when you're done. The home screen and the clean-up keep working without it. Turn it on again
        any time you want to change something from this app.
      </p>

      <div className="card white">
        <h2>1. Проверка перезапуска</h2>
        <p className="muted">
          Перезапустить телевизор once while debugging is still on. If it comes back to a black screen, the app can still put the stock
          launcher back. With debugging off, only a factory reset could.
        </p>
        <div className="row">
          <button className="btn primary" onClick={restartCheck} disabled={restarting || status.state !== 'connected'}>
            Перезапустить и проверить
          </button>
          {restarting && <Busy text="Перезапуск телевизора и ожидание главного экрана. Это может занять до 3 минут." />}
          {!restarting && status.state !== 'connected' && !boot && <span className="small muted">Подключить to the TV first.</span>}
        </div>
        {boot && (
          <div className={`notice ${boot.ok ? 'ok' : 'error'}`} style={{ marginTop: 16, marginBottom: 0 }}>
            <b>{boot.summary}</b> {boot.advice}
          </div>
        )}
        {!boot?.ok && (
          <label className="row small" style={{ marginTop: 12, cursor: 'pointer' }}>
            <input type="checkbox" checked={sawДомой} onChange={(e) => setSawДомой(e.target.checked)} />
            <span>Я самостоятельно перезапустил телевизор, и главный экран появился</span>
          </label>
        )}
      </div>

      <div className="card" style={safe ? undefined : { opacity: 0.5, pointerEvents: 'none' }} aria-disabled={!safe}>
        <h2>2. На телевизоре</h2>
        {!safe && <p className="small muted">Сначала выполните проверку перезапуска.</p>}
        <ul className="checklist">
          {STEPS.map((s, i) => (
            <li key={i} className="check" style={{ gridTemplateColumns: 'auto 1fr' }}>
              <label className="row" style={{ cursor: 'pointer', gridColumn: '1 / 3' }}>
                <input type="checkbox" checked={ticks[i]} onChange={(e) => setTicks(ticks.map((t, j) => (j === i ? e.target.checked : t)))} />
                <span style={{ textDecoration: ticks[i] ? 'line-through' : undefined, opacity: ticks[i] ? 0.6 : 1 }}>{s}</span>
              </label>
            </li>
          ))}
        </ul>
        {status.state === 'connected' && (
          <p className="small muted" style={{ marginTop: 10 }}>
            The app is still connected. Turning debugging off ends the connection, which is expected.
          </p>
        )}
      </div>

      <div className="card white">
        <h2>Если понадобится вернуться позже</h2>
        <p className="muted" style={{ marginBottom: 8 }}>
          Чтобы изменить обои, порядок приложений или другие параметры главного экрана:
        </p>
        <ol className="small" style={{ margin: '0 0 8px', paddingВлево: 20 }}>
          <li>На телевизоре, turn USB debugging on again (and Network or ADB debugging if your TV has it).</li>
          <li>Open this app. It reconnects to the TV by itself, finds it again if its address changed, and opens the Главный экран step.</li>
          <li>Если вы отозвали разрешения, снова подтвердите подключение на телевизоре.</li>
          <li>Внесите изменения и снова отключите отладку.</li>
        </ol>
        <p className="small muted" style={{ margin: 0 }}>
          The app reads what is on the TV before you edit, so you always start from the current wallpaper and order. Some Google TVs
          that needed a pairing code close port 5555 when they restart; the app shows the pairing form again if so.
        </p>
      </div>

      <div className="card white">
        <h2>3. Проверка с этого компьютера</h2>
        <p className="muted">После перезапуска телевизора проверьте, что порт 5555 больше не отвечает.</p>
        <div className="row">
          <input type="text" value={host} onChange={(e) => setHost(e.target.value)} placeholder="192.168.1.50" style={{ width: 200 }} />
          <button className="btn primary" onClick={check} disabled={!safe || checking || !/^\d{1,3}(\.\d{1,3}){3}$/.test(host.trim())}>
            Проверить, что порт 5555 закрыт
          </button>
          {checking && <Busy text="Проверка порта 5555" />}
        </div>
        {res && (
          <div className={`notice ${res.closed ? 'ok' : 'warn'}`} style={{ marginTop: 16, marginBottom: 0 }}>
            <b>{res.closed ? 'Закрыт.' : 'Всё ещё открыт.'}</b> {res.message}
          </div>
        )}
      </div>
    </>
  )
}
