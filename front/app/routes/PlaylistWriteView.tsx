import { useParams } from "react-router";
import AppShell from "~/components/layout/AppShell";
import SaveIcon from "~/assets/save.svg?react";
import QuestionMarkSquareIcon from "~/assets/question-mark-squre.svg?react";
import DeleteIcon from "~/assets/delete.svg?react";
import StarIcon from "~/assets/star.svg?react";
import FilledStarIcon from "~/assets/filled-star.svg?react";
import Modal from "~/components/ui/Modal";
import {useEffect, useRef, useState} from "react";
import {useNavigate} from "react-router";
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import Column1 from "~/components/layout/Column1";
import Column2 from "~/components/layout/Column2";
import TrackCreateLayer, {type Track} from "~/components/ui/TrackEditLayer";
import {createPlaylist, fetchPlaylistWithTracks, modifyPlaylist} from "~/utils/api";
import type { AxiosError } from "axios";
import { toast } from "sonner";
import Button from "~/components/ui/Button";

export default function PlaylistWriteView() {
	const { playlistId } = useParams();
	const [isBackModalOpen, setIsBackModalOpen] = useState(false)
	const [selectedTrack, setSelectedTrack] = useState<Track | null>(null)
	const [selectedTrackIndex, setSelectedTrackIndex] = useState<number | null>(null)
	const [isOpenEditTrack, setIsOpenEditTrack] = useState(false)
	const [title, setTitle] = useState("")
	const [description, setDescription] = useState("")
	const [tracks, setTracks] = useState<Track[]>([])
	const [representativeIndex, setRepresentativeIndex] = useState<number | null>(null)
	const descriptionRef = useRef<HTMLTextAreaElement>(null)
	const navigate = useNavigate()
	const maxTitleLength = 100
	const maxDescriptionLength = 500

	useEffect(() => {
		if (!playlistId) {
			return
		}
		const playlistIdNumber = parseInt(playlistId);
		if (!playlistIdNumber) {
			return
		}

		fetchPlaylistWithTracks(playlistIdNumber).then(playlist => {
			setTitle(playlist.title)
			setDescription(playlist.description)
			setTracks(playlist.tracks.map(track => ({
				embedId: track.embedId,
				title: track.title,
				startTimeSec: track.startTimeSec,
				endTimeSec: track.endTimeSec,
				repeatCount: track.repeatCount,
				additionalTitles: track.additionalTitles,
			})))
			const representativeTrackIndex = playlist.tracks.findIndex(track => track.isRepresentative);
			if (representativeTrackIndex !== -1) {
				setRepresentativeIndex(representativeTrackIndex)
			} else if (playlist.tracks.length > 0) {
				setRepresentativeIndex(0)
			} else {
				setRepresentativeIndex(null)
			}
		})

	}, [playlistId]);

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
		return tracks.reduce((acc, track) => acc + (track.endTimeSec - track.startTimeSec) * track.repeatCount, 0);
	}

	function deleteTrack(index: number) {
		if (representativeIndex === null) {
			return
		}

		if (index === representativeIndex) {
			if (tracks.length > 1) {
				setRepresentativeIndex(0)
			} else {
				setRepresentativeIndex(null)
			}
		} else if (index < representativeIndex) {
			setRepresentativeIndex(representativeIndex - 1)
		}

		const newPlaylist = tracks.filter((_, i) => i !== index);
		setTracks(newPlaylist);
	}

	async function onClickSave() {
		if (title.trim() === "") {
			toast.error("플레이리스트 이름을 입력해주세요.")
			return
		}
		if (title.trim().length > maxTitleLength) {
			toast.error(`플레이리스트 이름은 ${maxTitleLength}자를 초과할 수 없습니다.`)
			return
		}
		if (description.trim() === "") {
			toast.error("플레이리스트 소개를 입력해주세요.")
			return
		}
		if (description.trim().length > maxDescriptionLength) {
			toast.error(`플레이리스트 소개는 ${maxDescriptionLength}자를 초과할 수 없습니다.`)
			return
		}
		if (tracks.length === 0) {
			toast.error("플레이리스트에 곡을 추가해주세요.")
			return
		}
		if (representativeIndex === null) {
			toast.error("대표곡을 선택해주세요.")
			return
		}

		try {
			const request = {
				title: title.trim(),
				description: description.trim(),
				tracks: tracks.map((track, index) => ({
					embedId: track.embedId,
					title: track.title.trim(),
					startTimeSec: track.startTimeSec,
					endTimeSec: track.endTimeSec,
					repeatCount: track.repeatCount,
					additionalTitles: track.additionalTitles.map(title => title.trim()),
					isRepresentative: index === representativeIndex
				}))
			};

			if (playlistId && parseInt(playlistId)) {
				await modifyPlaylist(parseInt(playlistId), request);
			} else {
				await createPlaylist(request);
			}
			toast.success("플레이리스트가 저장되었습니다.")
			navigate("/playlists");
		} catch (error) {
			const axiosError = error as AxiosError<{message: string}>;
			toast.error(axiosError.response?.data?.message ?? "알 수 없는 오류가 발생했습니다.");
		}
	}

	return (
		<AppShell
			variant="sub"
			title="새 플레이리스트"
			onBack={() => setIsBackModalOpen(true)}
			actions={[{ icon: <SaveIcon />, label: "저장하기", onClick: onClickSave }]}
		>
			<ColumnsContainer>
				<Column1>
					<p className="hidden md:block text-4xl font-bold p-4">새 플레이리스트</p>
					<div className="flex flex-col px-6 gap-2">
						<p className="text-2xl font-bold">썸네일</p>
						<p className="px-2 text-zinc-400">썸네일은 대표곡 기준으로 표시됩니다.</p>
						<div className="mx-auto w-full max-w-48 md:max-w-96 aspect-square">
							{
								representativeIndex !== null ?
									<img className="w-full h-full object-contain rounded-2xl" src={`https://img.youtube.com/vi/${tracks[representativeIndex].embedId}/maxresdefault.jpg`} alt={tracks[representativeIndex].title} draggable={false}/>
									: <QuestionMarkSquareIcon className="w-full h-full"></QuestionMarkSquareIcon>
							}
						</div>
					</div>
					<div className="flex flex-col px-4 gap-2">
						<p className="text-2xl font-bold">이름</p>
						<div className="w-full h-10 p-2 shrink bg-surface border border-border text-zinc-200 rounded-full focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
							<input
								type="text"
								placeholder="이름"
								value={title}
								onChange={(e) => setTitle(e.target.value)}
								maxLength={maxTitleLength}
								className="w-full pl-[8px] placeholder-zinc-500 focus:outline-none"
							/>
						</div>
					</div>
					<div className="flex flex-col px-4 gap-2">
						<p className="text-2xl font-bold">소개</p>
						<div className="w-full p-2 shrink bg-surface border border-border text-zinc-200 rounded-2xl focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
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
								className="w-full pl-[8px] placeholder-zinc-500 resize-none overflow-hidden focus:outline-none"
							/>
						</div>
					</div>
				</Column1>
				<Column2>
					<div className="flex flex-row gap-2 mt-4 md:mt-22 items-end px-4">
						<p className="text-2xl font-bold">곡 목록</p>
						<p>{`${tracks.length}곡(최대 ${Math.ceil(expectedPlaytime() / 60)}분)`}</p>
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
								tracks.map((track: Track, index: number) => (
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
						<Button variant="primary" fullWidth onClick={goBack}>확인</Button>
						<Button variant="secondary" fullWidth onClick={() => setIsBackModalOpen(false)}>취소</Button>
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
					playlist={tracks}
					setPlaylist={setTracks}
					selectedIndex={selectedTrackIndex}
					onClose={() => setIsOpenEditTrack(false)}
					representativeIndex={representativeIndex}
					setRepresentativeIndex={setRepresentativeIndex}
				>
				</TrackCreateLayer>
			</Modal>
		</AppShell>
	)
}
