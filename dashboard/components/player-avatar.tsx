"use client"

export function PlayerAvatar({
  uuid,
  username,
  avatar,
  sizePx,
}: {
  uuid: string
  username?: string | null
  avatar?: string | null
  sizePx: number
}) {
  const style = { width: sizePx, height: sizePx }

  if (avatar && /^https?:\/\//i.test(avatar)) {
    return (
      <div
        className="rounded-sm shrink-0"
        style={{
          ...style,
          backgroundImage: `url(${avatar})`,
          backgroundSize: `${sizePx * 8}px ${sizePx * 8}px`,
          backgroundPosition: "0 0",
          imageRendering: "pixelated",
        }}
        title="Custom Avatar"
      />
    )
  }

  const name = (avatar && !avatar.includes("/") ? avatar : "") || username || uuid
  return (
    <img
      className="rounded-sm shrink-0"
      style={style}
      loading="lazy"
      alt=""
      src={`https://mc-heads.net/avatar/${encodeURIComponent(name)}/${Math.max(8, Math.min(256, sizePx))}`}
      onError={(e) => { (e.target as HTMLImageElement).style.visibility = "hidden" }}
    />
  )
}
