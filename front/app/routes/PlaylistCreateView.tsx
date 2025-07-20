import NavigationBar from "~/components/layout/NavigationBar";
import NavigationItem from "~/components/layout/NavigationItem";
import Me from "~/components/ui/Me";
import BackArrowIcon from "~/assets/back-arrow.svg?react";
import SaveIcon from "~/assets/save.svg?react";
import QuestionMarkSquareIcon from "~/assets/question-mark-squre.svg?react";
import DeleteIcon from "~/assets/delete.svg?react";
import StarIcon from "~/assets/star.svg?react";
import FilledStarIcon from "~/assets/filled-star.svg?react";
import Modal from "~/components/ui/Modal";
import {useRef, useState} from "react";
import {useNavigate} from "react-router";
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import Column1 from "~/components/layout/Column1";
import Column2 from "~/components/layout/Column2";
import TrackCreateLayer, {type Track} from "~/components/ui/TrackEditLayer";
import { createPlaylist } from "~/utils/api";
import type { AxiosError } from "axios";

export default function PlaylistCreateView() {
	const [isBackModalOpen, setIsBackModalOpen] = useState(false)
	const [selectedTrack, setSelectedTrack] = useState<Track | null>(null)
	const [selectedTrackIndex, setSelectedTrackIndex] = useState<number | null>(null)
	const [isOpenEditTrack, setIsOpenEditTrack] = useState(false)
	const [title, setTitle] = useState("")
	const [description, setDescription] = useState("")
	const [playlist, setPlaylist] = useState<Track[]>([])
	const [representativeIndex, setRepresentativeIndex] = useState<number | null>(null)
	const [isAlertModalOpen, setIsAlertModalOpen] = useState(false)
	const [alertMessage, setAlertMessage] = useState("")
	const descriptionRef = useRef<HTMLTextAreaElement>(null)
	const navigate = useNavigate()
	const maxTitleLength = 100
	const maxDescriptionLength = 500

	function goBack() {
		navigate(-1)
	}

	const handleDescriptionInput = () => {
		if (descriptionRef.current) {
			descriptionRef.current.style.height = "auto";
			descriptionRef.current.style.height = `${descriptionRef.current.scrollHeight}px`
		}
	}

	function expectedPlaytime() {
		return playlist.reduce((acc, track) => acc + (track.endTimeSec - track.startTimeSec) * track.repeatCount, 0);
	}

	function deleteTrack(index: number) {
		if (representativeIndex === null) {
			return
		}

		if (index === representativeIndex) {
			if (playlist.length > 1) {
				setRepresentativeIndex(0)
			} else {
				setRepresentativeIndex(null)
			}
		} else if (index < representativeIndex) {
			setRepresentativeIndex(representativeIndex - 1)
		}

		const newPlaylist = playlist.filter((_, i) => i !== index);
		setPlaylist(newPlaylist);
	}

	async function onClickSave() {
		if (title.trim() === "") {
			setAlertMessage("플레이리스트 이름을 입력해주세요.")
			setIsAlertModalOpen(true)
			return
		}
		if (title.trim().length > maxTitleLength) {
			setAlertMessage(`플레이리스트 이름은 ${maxTitleLength}자를 초과할 수 없습니다.`)
			setIsAlertModalOpen(true)
			return
		}
		if (description.trim() === "") {
			setAlertMessage("플레이리스트 소개를 입력해주세요.")
			setIsAlertModalOpen(true)
			return
		}
		if (description.trim().length > maxDescriptionLength) {
			setAlertMessage(`플레이리스트 소개는 ${maxDescriptionLength}자를 초과할 수 없습니다.`)
			setIsAlertModalOpen(true)
			return
		}
		if (playlist.length === 0) {
			setAlertMessage("플레이리스트에 곡을 추가해주세요.")
			setIsAlertModalOpen(true)
			return
		}
		if (representativeIndex === null) {
			setAlertMessage("대표곡을 선택해주세요.")
			setIsAlertModalOpen(true)
			return
		}

		try {
			const request = {
				title: title.trim(),
				description: description.trim(),
				tracks: playlist.map((track, index) => ({
					embedId: track.embedId,
					title: track.title.trim(),
					startTimeSec: track.startTimeSec,
					endTimeSec: track.endTimeSec,
					repeatCount: track.repeatCount,
					additionalTitles: track.additionalTitles.map(title => title.trim()),
					isRepresentative: index === representativeIndex
				}))
			};
			
			await createPlaylist(request);
			navigate("/playlists");
		} catch (error) {
			const axiosError = error as AxiosError<{message: string}>;
			setAlertMessage(axiosError.response?.data?.message ?? "알 수 없는 오류가 발생했습니다.");
			setIsAlertModalOpen(true);
		}
	}

	return (
		<div className="flex flex-row w-full h-full">
			<NavigationBar>
				<div className="grow shrink flex flex-col gap-4">
					<NavigationItem onClick={() => setIsBackModalOpen(true)} icon={<BackArrowIcon></BackArrowIcon>} title={"뒤로가기"}></NavigationItem>
					<NavigationItem onClick={onClickSave} icon={<SaveIcon></SaveIcon>} title={"저장하기"}></NavigationItem>
				</div>
				<div className="grow-0 shrink-0"><Me></Me></div>
			</NavigationBar>
			<ColumnsContainer>
				<Column1>
					<p className="text-4xl font-bold p-4">새 플레이리스트</p>
					<div className="flex flex-col px-6 gap-2">
						<p className="text-2xl font-bold">썸네일</p>
						<p className="px-2 text-zinc-400">썸네일은 대표곡 기준으로 표시됩니다.</p>
						<div className="mx-auto w-full max-w-96 aspect-square">
							{
								representativeIndex !== null ?
									<img className="w-full h-full object-contain rounded-2xl" src={`https://img.youtube.com/vi/${playlist[representativeIndex].embedId}/maxresdefault.jpg`} alt={playlist[representativeIndex].title} draggable={false}/>
									: <QuestionMarkSquareIcon className="w-full h-full"></QuestionMarkSquareIcon>
							}
						</div>
					</div>
					<div className="flex flex-col px-4 gap-2">
						<p className="text-2xl font-bold">이름</p>
						<div className="w-full h-10 p-2 shrink bg-zinc-600 text-zinc-200 rounded-full">
							<input
								type="text"
								placeholder="이름"
								value={title}
								onChange={(e) => setTitle(e.target.value)}
								maxLength={maxTitleLength}
								className="w-full pl-[8px] focus:outline-none"
							/>
						</div>
					</div>
					<div className="flex flex-col px-4 gap-2">
						<p className="text-2xl font-bold">소개</p>
						<div className="w-full p-2 shrink bg-zinc-600 text-zinc-200 rounded-2xl">
                            <textarea
								rows={1}
								ref={descriptionRef}
								placeholder="소개"
								value={description}
								onKeyDown={e => {
									if (e.key === "Enter") {
										e.preventDefault();
									}
								}}
								onInput={handleDescriptionInput}
								onChange={(e) => setDescription(e.target.value)}
								maxLength={maxDescriptionLength}
								className="w-full pl-[8px] resize-none overflow-hidden focus:outline-none"
							/>
						</div>
					</div>
				</Column1>
				<Column2>
					<div className="flex flex-row gap-2 mt-22 items-end px-4">
						<p className="text-2xl font-bold">곡 목록</p>
						<p>{`${playlist.length}곡(최대 ${Math.ceil(expectedPlaytime() / 60)}분)`}</p>
					</div>
					<div className="w-full flex-1 px-4 py-2">
						<div className="w-full h-full p-8 bg-zinc-800 text-zinc-200 rounded-2xl flex flex-col gap-2">
							<div
								className="px-auto py-6 border-1 text-6xl font-bold text-center hover:bg-zinc-700 rounded-lg cursor-pointer"
								onClick={() => {
									setSelectedTrack(null)
									setSelectedTrackIndex(null)
									setIsOpenEditTrack(true)
								}}
							>+
							</div>
							{
								playlist.map((track: Track, index: number) => (
									<div className="flex flex-row items-center p-2 hover:bg-zinc-700 rounded-lg cursor-pointer gap-4"
										 onClick={() => {
											 setSelectedTrack(track)
											 setSelectedTrackIndex(index)
											 setIsOpenEditTrack(true)
										 }}
									>
										<img className="h-24" src={`https://img.youtube.com/vi/${track.embedId}/mqdefault.jpg`} alt={track.title} draggable={false}/>
										<div className="flex flex-1 flex-col">
											<p className="text-lg font-bold">{track.title}</p>
											<p className="text-sm text-zinc-400">{track.additionalTitles.join(", ")}</p>
										</div>
										{
											(representativeIndex !== null && representativeIndex === index)
												? <FilledStarIcon></FilledStarIcon>
												: <StarIcon className="cursor-pointer" onClick={e => {e.stopPropagation(); setRepresentativeIndex(index);}}></StarIcon>
										}
										<DeleteIcon onClick={e => {e.stopPropagation(); deleteTrack(index);}}></DeleteIcon>
									</div>
								))
							}
						</div>
					</div>
				</Column2>
			</ColumnsContainer>
			<Modal isOpen={isBackModalOpen} onClose={() => setIsBackModalOpen(false)}>
				<div className="flex flex-col w-full">
					<h2 className="text-xl font-bold mb-4">뒤로가기</h2>
					<p>수정 중인 플레이리스트를 저장하지 않고 뒤로 가시겠습니까?</p>
					<div className="flex flex-row mt-6 gap-2">
						<button
							className="rounded-full p-2 grow bg-cyan-400 text-zinc-900 font-bold cursor-pointer"
							onClick={goBack}
						>확인
						</button>
						<button
							className="rounded-full p-2 grow bg-zinc-200 text-zinc-900 font-bold cursor-pointer"
							onClick={() => setIsBackModalOpen(false)}
						>취소
						</button>
					</div>
				</div>
			</Modal>
			<Modal isOpen={isAlertModalOpen} onClose={() => setIsAlertModalOpen(false)}>
				<div className="flex flex-col w-full">
					<p>{alertMessage}</p>
					<div className="flex flex-row mt-6 gap-2">
						<button
							className="rounded-full p-2 grow bg-cyan-400 text-zinc-900 font-bold cursor-pointer"
							onClick={() => setIsAlertModalOpen(false)}
						>닫기
						</button>
					</div>
				</div>
			</Modal>
			<Modal isOpen={isOpenEditTrack} onClose={() => {
			}}>
				<TrackCreateLayer
					embedId={selectedTrack?.embedId ?? ""}
					title={selectedTrack?.title ?? ""}
					startTimeSec={selectedTrack?.startTimeSec ?? 0}
					endTimeSec={selectedTrack?.endTimeSec ?? 0}
					repeatCount={selectedTrack?.repeatCount ?? 1}
					additionalTitles={selectedTrack?.additionalTitles ?? Array<string>()}
					playlist={playlist}
					setPlaylist={setPlaylist}
					selectedIndex={selectedTrackIndex}
					onClose={() => setIsOpenEditTrack(false)}
					representativeIndex={representativeIndex}
					setRepresentativeIndex={setRepresentativeIndex}
				>
				</TrackCreateLayer>
			</Modal>
		</div>
	)
}
