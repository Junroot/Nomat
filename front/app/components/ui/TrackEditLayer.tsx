import { useEffect, useState } from "react"
import TimePicker from "./TimePicker"
import AdditionalTitleEditor from "./AdditionalTitleEditor"
import CloseIcon from "~/assets/close.svg?react"
import StarIcon from "~/assets/star.svg?react"
import FilledStarIcon from "~/assets/filled-star.svg?react"
import Dropdown from "./Dropdown"
import Button from "./Button"
import { getEmbedIdByUrl, getUrlByEmbedId } from "~/utils/youtube"
import YouTube from "react-youtube"

export interface Track {
    embedId: string,
    title: string,
    startTimeSec: number,
    endTimeSec: number,
    repeatCount: number,
    additionalTitles: Array<string>,
}

interface TrackEditLayerProps {
    embedId: string,
    title: string,
    startTimeSec: number,
    endTimeSec: number,
    repeatCount: number,
    additionalTitles: Array<string>,
    playlist: Track[],
    setPlaylist: (value: Track[]) => void,
	representativeIndex: number|null,
	setRepresentativeIndex: (value: number|null) => void,
    selectedIndex: number|null,
    onClose: () => void,
}

export default function TrackCreateLayer(props: TrackEditLayerProps) {
    const [youTubeUrl, setYouTubeUrl] = useState(props.embedId ? getUrlByEmbedId(props.embedId) : "")
    const [title, setTitle] = useState(props.title)
    const [startTimeSec, setStartTimeSec] = useState(props.startTimeSec)
    const [endTimeSec, setEndTimeSec] = useState(props.endTimeSec)
    const [maxEndTimeSec, setMaxEndTimeSec] = useState(0);
    const [repeatCount, setRepeatCount] = useState(props.repeatCount.toString())
    const [additionalTitles, setAdditionalTitles] = useState(props.additionalTitles)
    const [youTubeOptions, setYouTubeOptions] = useState(
        {
			width: '320',
			height: '180',
            playerVars: {
                cc_load_policy: 0,
                controls: 1,
                disablekb: 1,
                iv_load_policy: 3,
                modestbranding: 1,
                fs: 0,
                rel: 0,
                showinfo: 1,
                playsinline: 1,
                loop: 1,
                start: startTimeSec,
                end: endTimeSec,
            }
        }
    )
    const maxTitleLength = 100
    const maxAdditoinalTitlesCount = 10

    useEffect(() => {
        setYouTubeOptions(
            {
				width: '320',
				height: '180',
                playerVars: {
                    cc_load_policy: 0,
                    controls: 1,
                    disablekb: 1,
                    iv_load_policy: 3,
                    modestbranding: 1,
                    fs: 0,
                    rel: 0,
                    showinfo: 1,
                    playsinline: 1,
                    loop: 1,
                    start: startTimeSec,
                    end: endTimeSec,
                }
            }
        )
    }, [startTimeSec, endTimeSec]);

    function submit() {
        const embedId = getEmbedIdByUrl(youTubeUrl)
        if (embedId == null) {
            return
        }
        const newTrack: Track = {
            embedId,
            title,
            startTimeSec,
            endTimeSec,
            repeatCount: parseInt(repeatCount),
            additionalTitles,
        }

        if (props.selectedIndex == null) {
            props.setPlaylist([...props.playlist, newTrack])
        } else {
            props.setPlaylist([
                ...props.playlist.slice(0, props.selectedIndex),
                newTrack,
                ...props.playlist.slice(props.selectedIndex + 1)
            ])
        }

		if (props.representativeIndex === null) {
			props.setRepresentativeIndex(0)
		}
        props.onClose()
    }

    function isValidInput() {
        return isValidYoutubeUrl() && isValidTitle() && isValidPlayTime()
    }

    function isValidYoutubeUrl() {
        return maxEndTimeSec > 0
    }

    function isValidTitle() {
        return title.length > 0
    }

    function isValidPlayTime() {
        return startTimeSec < endTimeSec && endTimeSec <= maxEndTimeSec
    }

    function onYouTubeReady(event: any) {
        const duration = event.target.getDuration()
        setMaxEndTimeSec(duration)
        setEndTimeSec(duration)
    }

	function isRepresentative() {
		return isEditing() && props.representativeIndex !== null && props.representativeIndex === props.selectedIndex
	}

	function isEditing(){
		return props.selectedIndex !== null
	}

    return (
        <div className="flex flex-col">
			<div className="flex flex-row">
				<h2 className="flex-1 text-xl font-bold mb-4">{ isEditing() ? "곡 편집" : "곡 추가"}</h2>
				{ isEditing()
					&& (isRepresentative() ? <FilledStarIcon></FilledStarIcon>
						: <StarIcon className="cursor-pointer" onClick={() => props.setRepresentativeIndex(props.selectedIndex)}></StarIcon>) }
			</div>
			<div className="flex flex-row items-center gap-2">
				<div className="p-2">
					{ getEmbedIdByUrl(youTubeUrl) ?
						<YouTube videoId={getEmbedIdByUrl(youTubeUrl) ?? ""} opts={youTubeOptions} onReady={onYouTubeReady}></YouTube>
						: <div className="w-80 h-45 bg-black"></div>
					}
				</div>
				<div className="flex flex-col gap-2 w-2xl">
					<div className="flex flex-col px-4 gap-1">
						<p className="px-4">YouTube URL</p>
						<div className="flex-1 h-10 p-2 shrink bg-surface border border-border text-zinc-200 rounded-full focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
							<input
								type="text"
								placeholder="YouTube URL"
								value={youTubeUrl}
								onChange={(e) => {setYouTubeUrl(e.target.value)}}
								maxLength={100}
								className="w-full pl-[8px] placeholder-zinc-500 focus:outline-none"
							/>
						</div>
						<p className="px-4 text-xs text-red-600 h-2">{isValidYoutubeUrl() ? " " : "올바른 URL을 입력해 주세요."}</p>
					</div>
					<div className="flex flex-col px-4 gap-1">
						<p className="px-4">제목</p>
						<div className="flex-1 h-10 p-2 shrink bg-surface border border-border text-zinc-200 rounded-full focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
							<input
								type="text"
								placeholder="제목"
								value={title}
								onChange={(e) => {setTitle(e.target.value)}}
								maxLength={maxTitleLength}
								className="w-full pl-[8px] placeholder-zinc-500 focus:outline-none"
							/>
						</div>
						<p className="px-4 text-xs text-red-600 h-2">{isValidTitle() ? " " : "제목을 입력해 주세요."}</p>
					</div>
					<div className="flex flex-col px-4 gap-1">
						<p className="px-4">재생 시간</p>
						<div className="flex flex-row items-center gap-2">
							<TimePicker timeSec={startTimeSec} setTimeSec={setStartTimeSec}></TimePicker>
							<p>-</p>
							<TimePicker timeSec={endTimeSec} setTimeSec={setEndTimeSec}></TimePicker>
						</div>
						<p className="px-4 text-xs text-red-600 h-2">{isValidPlayTime() ? " " : "재생 시간이 올바르지 않습니다."}</p>
					</div>
					<div className="flex flex-col px-4 gap-1">
						<p className="px-4">반복 횟수</p>
						<div className="flex-1">
							<Dropdown values={["1", "2", "3", "4", "5"]} selectedValue={repeatCount} setValue={setRepeatCount} ></Dropdown>
						</div>
						<p className="px-4 text-xs text-red-600 h-2"></p>
					</div>
					<div className="flex flex-col px-4 gap-1">
						<p className="px-4">추가 정답 <span>({additionalTitles.length}/{maxAdditoinalTitlesCount})</span></p>
						<AdditionalTitleEditor maxAdditionalTitlesCount={maxAdditoinalTitlesCount} additionalTitles={additionalTitles} setAdditionalTitles={setAdditionalTitles}></AdditionalTitleEditor>
					</div>
					<div className="flex-1 flex flex-row gap-1 flex-wrap text-nowrap px-4 py-1">
						{
							additionalTitles.map((title, index) =>
								<button key={index} className="flex flex-row items-center bg-zinc-600 rounded-full px-3 gap-1 overflow-hidden">
									<p className="truncate whitespace-nowrap">{title}</p>
									<CloseIcon className="size-4 cursor-pointer" fill="red" onClick={() => setAdditionalTitles([...additionalTitles.slice(0, index), ...additionalTitles.slice(index + 1)])}></CloseIcon>
								</button>
							)
						}
					</div>
				</div>
			</div>
            <div className="flex flex-row mt-6 gap-2">
                <Button variant="primary" fullWidth disabled={!isValidInput()} onClick={submit}>확인</Button>
                <Button variant="secondary" fullWidth onClick={props.onClose}>취소</Button>
            </div>
        </div>
    )
}
